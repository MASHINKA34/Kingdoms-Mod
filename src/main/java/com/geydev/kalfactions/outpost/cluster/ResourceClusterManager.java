package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.outpost.cluster.distribution.ClusterResource;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceDistribution;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceDistributionConfig;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceCycleSchedule;
import com.geydev.kalfactions.outpost.cluster.distribution.FiniteResourceLedger;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class ResourceClusterManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_resource_clusters";
    public static final Factory<ResourceClusterManager> FACTORY =
            new Factory<>(ResourceClusterManager::new, ResourceClusterManager::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CELL_SIZE_CHUNKS = 16;
    private static final int LOAD_DELAY_TICKS = 20;
    private static final int MAX_PLACEMENTS_PER_TICK = 16;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long CELL_X_SALT = 0x632BE59BD9B4E019L;
    private static final long CELL_Z_SALT = 0x8CB92BA72F3D8DD7L;
    private static final String TAG_CLUSTERS = "clusters";
    private static final String TAG_REMOVED = "removed";
    private static final int DATA_VERSION = 2;
    private static final String TAG_VERSION = "formatVersion";
    private static final String TAG_DEPOSITS = "oreDeposits";
    private static final String TAG_CYCLE = "resourceCycleId";
    private static final String TAG_NEXT_CYCLE = "nextResourceCycle";
    private static final String TAG_PAUSED = "resourceQueuePaused";
    private static final ZoneId RESOURCE_ZONE_ID = ZoneId.of("Europe/Moscow");
    private static final String ENTITY_CLUSTER_KEY = KalFactions.MOD_ID + "ResourceCluster";
    private static final String ENTITY_ROLE_KEY = KalFactions.MOD_ID + "ResourceClusterRole";
    private static final String ITEM_ROLE = "item";
    private static final String TEXT_ROLE = "text";

    private final Map<Long, ResourceCluster> clusters = new LinkedHashMap<>();
    private final Map<Long, Long> pendingChunks = new HashMap<>();
    private final Set<Long> activeChunks = new HashSet<>();
    private final Map<Long, Long> boundDrill = new HashMap<>();
    private final Set<Long> removedChunks = new HashSet<>();
    private final Map<UUID, OreDeposit> oreDeposits = new LinkedHashMap<>();
    private final Map<Long, UUID> depositCenters = new HashMap<>();
    private final Map<Long, UUID> trackedOre = new HashMap<>();
    private final Deque<UUID> generationQueue = new ArrayDeque<>();
    private final Deque<UUID> cleanupQueue = new ArrayDeque<>();
    private long resourceCycleId;
    private long nextResourceCycleMillis;
    private boolean resourceQueuePaused;

    public static ResourceClusterManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Optional<ClusterView> clusterAt(ChunkPos chunkPos) {
        ResourceCluster cluster = clusters.get(chunkPos.toLong());
        return cluster == null
                ? Optional.empty()
                : Optional.of(new ClusterView(cluster.type(), cluster.richness()));
    }

    public synchronized Optional<OreDepositView> oreDepositAt(ChunkPos chunkPos) {
        UUID id = depositCenters.get(chunkPos.toLong());
        OreDeposit deposit = id == null ? null : oreDeposits.get(id);
        return deposit == null || deposit.cycleId != resourceCycleId || deposit.state == DepositState.CLEANED
                ? Optional.empty()
                : Optional.of(deposit.view());
    }

    public synchronized DrillExtraction extractForDrill(ChunkPos chunkPos, int requested) {
        if (requested <= 0) {
            return DrillExtraction.empty();
        }
        UUID id = depositCenters.get(chunkPos.toLong());
        OreDeposit deposit = id == null ? null : oreDeposits.get(id);
        if (deposit == null || deposit.cycleId != resourceCycleId || deposit.state == DepositState.CLEANING
                || deposit.state == DepositState.CLEANED || deposit.remaining <= 0) {
            return DrillExtraction.empty();
        }
        FiniteResourceLedger.Extraction extraction = FiniteResourceLedger.extract(deposit.remaining, requested);
        int extracted = extraction.extracted();
        deposit.remaining = extraction.remaining();
        if (deposit.remaining == 0) {
            markDepleted(deposit, System.currentTimeMillis());
        }
        setDirty();
        return new DrillExtraction(deposit.resource, extracted, deposit.remaining, deposit.originalReserve);
    }

    public synchronized OreConsumption consumeTrackedOre(ServerLevel level, BlockPos pos) {
        UUID id = trackedOre.remove(pos.asLong());
        if (id == null) {
            return OreConsumption.NOT_TRACKED;
        }
        OreDeposit deposit = oreDeposits.get(id);
        if (deposit == null || !deposit.createdPositions.remove(pos.asLong())) {
            return OreConsumption.NOT_TRACKED;
        }
        boolean hadReserve = deposit.remaining > 0;
        if (deposit.remaining > 0) {
            deposit.remaining--;
        }
        if (deposit.remaining == 0) {
            markDepleted(deposit, System.currentTimeMillis());
        }
        setDirty();
        if (!hadReserve) {
            level.setBlock(pos, naturalBase(pos.getY()).defaultBlockState(), 2);
            return OreConsumption.DEPLETED_NO_DROP;
        }
        return OreConsumption.CHARGED;
    }

    public synchronized long resourceCycleId() {
        return resourceCycleId;
    }

    public synchronized long nextResourceCycleMillis() {
        return nextResourceCycleMillis;
    }

    public synchronized boolean resourceQueuePaused() {
        return resourceQueuePaused;
    }

    public synchronized void setResourceQueuePaused(boolean paused) {
        if (resourceQueuePaused != paused) {
            resourceQueuePaused = paused;
            setDirty();
        }
    }

    public synchronized long advanceResourceCycle(long nowMillis) {
        resourceCycleId++;
        nextResourceCycleMillis = calculateNextCycleMillis(nowMillis);
        for (OreDeposit deposit : oreDeposits.values()) {
            if (deposit.state != DepositState.CLEANED) {
                deposit.state = DepositState.CLEANING;
                enqueueUnique(cleanupQueue, deposit.id);
            }
        }
        setDirty();
        return resourceCycleId;
    }

    public synchronized ResourceStatistics statistics() {
        Map<ResourceZone, Integer> byZone = new java.util.EnumMap<>(ResourceZone.class);
        Map<ClusterResource, Integer> byResource = new java.util.EnumMap<>(ClusterResource.class);
        int active = 0;
        int depleted = 0;
        for (OreDeposit deposit : oreDeposits.values()) {
            if (deposit.state == DepositState.CLEANED) {
                continue;
            }
            byZone.merge(deposit.zone, 1, Integer::sum);
            byResource.merge(deposit.resource, 1, Integer::sum);
            if (deposit.remaining > 0) {
                active++;
            } else {
                depleted++;
            }
        }
        return new ResourceStatistics(resourceCycleId, active, depleted, Map.copyOf(byZone), Map.copyOf(byResource));
    }

    public synchronized IntegrityReport verifyIntegrity() {
        int issues = 0;
        Set<Long> seen = new HashSet<>();
        for (OreDeposit deposit : oreDeposits.values()) {
            if (deposit.remaining < 0 || deposit.remaining > deposit.originalReserve
                    || deposit.generationIndex < 0 || deposit.generationIndex > deposit.plannedPositions.size()) {
                issues++;
            }
            for (long pos : deposit.createdPositions) {
                if (!seen.add(pos) || !deposit.id.equals(trackedOre.get(pos))) {
                    issues++;
                }
            }
        }
        for (Map.Entry<Long, UUID> entry : trackedOre.entrySet()) {
            OreDeposit deposit = oreDeposits.get(entry.getValue());
            if (deposit == null || !deposit.createdPositions.contains(entry.getKey())) {
                issues++;
            }
        }
        return new IntegrityReport(oreDeposits.size(), trackedOre.size(), generationQueue.size(), cleanupQueue.size(), issues);
    }

    public synchronized Optional<OreDepositView> nearestDeposit(BlockPos origin, int maxDistance) {
        long maxDistanceSquared = (long) maxDistance * maxDistance;
        OreDeposit nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (OreDeposit deposit : oreDeposits.values()) {
            if (deposit.state == DepositState.CLEANED) {
                continue;
            }
            long dx = (long) deposit.center.getX() - origin.getX();
            long dz = (long) deposit.center.getZ() - origin.getZ();
            long distance = dx * dx + dz * dz;
            if (distance <= maxDistanceSquared && distance < nearestDistance) {
                nearest = deposit;
                nearestDistance = distance;
            }
        }
        return nearest == null ? Optional.empty() : Optional.of(nearest.view());
    }

    public synchronized Optional<ResourceClusterType> clusterBlockAt(BlockPos pos) {
        ResourceCluster cluster = clusters.get(ChunkPos.asLong(pos));
        return cluster != null && isClusterColumn(cluster, pos)
                ? Optional.of(cluster.type())
                : Optional.empty();
    }

    public synchronized Optional<ResourceClusterType> removeCluster(ServerLevel level, BlockPos pos) {
        long key = ChunkPos.asLong(pos);
        ResourceCluster cluster = clusters.get(key);
        if (cluster == null || !isClusterColumn(cluster, pos)) {
            return Optional.empty();
        }
        for (int offset = 0; offset < 3; offset++) {
            BlockPos blockPos = cluster.basePos().above(offset);
            if (level.getBlockState(blockPos).is(cluster.type().block())) {
                level.removeBlock(blockPos, false);
            }
        }
        Display.ItemDisplay itemDisplay = findItemDisplay(level, cluster);
        if (itemDisplay != null) {
            itemDisplay.discard();
        }
        Display.TextDisplay textDisplay = findTextDisplay(level, cluster);
        if (textDisplay != null) {
            textDisplay.discard();
        }
        clusters.remove(key);
        pendingChunks.remove(key);
        activeChunks.remove(key);
        boundDrill.remove(key);
        removedChunks.add(key);
        setDirty();
        return Optional.of(cluster.type());
    }

    private static boolean isClusterColumn(ResourceCluster cluster, BlockPos pos) {
        BlockPos base = cluster.basePos();
        return pos.getX() == base.getX()
                && pos.getZ() == base.getZ()
                && pos.getY() >= base.getY()
                && pos.getY() <= base.getY() + 2;
    }

    public synchronized boolean bindDrill(ChunkPos chunkPos, BlockPos drillPos) {
        long key = chunkPos.toLong();
        Long existing = boundDrill.get(key);
        long posLong = drillPos.asLong();
        if (existing != null && existing != posLong) {
            return false;
        }
        if (existing == null) {
            boundDrill.put(key, posLong);
        }
        return true;
    }

    public synchronized void unbindDrill(ChunkPos chunkPos, BlockPos drillPos) {
        long key = chunkPos.toLong();
        Long existing = boundDrill.get(key);
        if (existing != null && existing == drillPos.asLong()) {
            boundDrill.remove(key);
        }
    }

    public synchronized boolean isBoundDrill(ChunkPos chunkPos, BlockPos drillPos) {
        Long existing = boundDrill.get(chunkPos.toLong());
        return existing != null && existing == drillPos.asLong();
    }

    public synchronized void queue(ChunkPos chunkPos, long gameTime) {
        pendingChunks.merge(chunkPos.toLong(), gameTime + LOAD_DELAY_TICKS, Math::min);
    }

    public synchronized void deactivate(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        pendingChunks.remove(key);
        activeChunks.remove(key);
    }

    public synchronized void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        checkResourceCycle();
        processPending(level, gameTime);
        if (!resourceQueuePaused) {
            processGeneration(level);
            processCleanup(level);
        }
        if ((gameTime & 1L) == 0L) {
            rotateItems(level);
        }
        if (gameTime % 200L == 0L) {
            repairActive(level);
        }
    }

    private void processPending(ServerLevel level, long gameTime) {
        List<Long> ready = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : pendingChunks.entrySet()) {
            if (entry.getValue() <= gameTime) {
                ready.add(entry.getKey());
                if (ready.size() >= MAX_PLACEMENTS_PER_TICK) {
                    break;
                }
            }
        }
        for (Long key : ready) {
            pendingChunks.remove(key);
            ChunkPos chunkPos = new ChunkPos(key);
            if (level.hasChunk(chunkPos.x, chunkPos.z)) {
                ensureCluster(level, chunkPos);
                ensureOreDeposit(level, chunkPos);
            }
        }
    }

    private void checkResourceCycle() {
        long now = System.currentTimeMillis();
        if (nextResourceCycleMillis <= 0L) {
            nextResourceCycleMillis = calculateNextCycleMillis(now);
            setDirty();
        }
        if (ModConfigSpec.RESOURCE_AUTO_CYCLE.getAsBoolean() && now >= nextResourceCycleMillis) {
            advanceResourceCycle(now);
        }
    }

    private static long calculateNextCycleMillis(long nowMillis) {
        return ResourceCycleSchedule.next(
                nowMillis,
                ModConfigSpec.RESOURCE_CYCLE_DAYS.getAsInt(),
                ModConfigSpec.RESOURCE_CYCLE_RESET_HOUR.getAsInt(),
                RESOURCE_ZONE_ID
        );
    }

    private void ensureOreDeposit(ServerLevel level, ChunkPos loadedChunk) {
        ResourceDistributionConfig config = distributionConfig();
        int centerX = loadedChunk.getMiddleBlockX();
        int centerZ = loadedChunk.getMiddleBlockZ();
        int cellX = Math.floorDiv(centerX, config.cellSize());
        int cellZ = Math.floorDiv(centerZ, config.cellSize());
        ResourceDistribution distribution = new ResourceDistribution(
                level.getSeed(),
                resourceCycleId,
                level.getSharedSpawnPos().getX(),
                level.getSharedSpawnPos().getZ(),
                config
        );
        ResourceDistribution.CellCandidate candidate = distribution.candidateForCell(cellX, cellZ).orElse(null);
        if (candidate == null) {
            return;
        }
        ChunkPos candidateChunk = new ChunkPos(candidate.blockX() >> 4, candidate.blockZ() >> 4);
        if (!candidateChunk.equals(loadedChunk) || depositCenters.containsKey(candidateChunk.toLong())) {
            return;
        }
        int depth = chooseDepth(level, candidate);
        BlockPos center = new BlockPos(candidate.blockX(), depth, candidate.blockZ());
        UUID id = namedUuid(level.getSeed() + ":ore:" + resourceCycleId + ":" + cellX + ":" + cellZ);
        if (oreDeposits.containsKey(id)) {
            return;
        }
        List<Long> planned = planShape(center, candidate.size(), candidate.shapeSeed(), candidateChunk);
        if (planned.isEmpty()) {
            return;
        }
        OreDeposit deposit = new OreDeposit(
                id,
                level.dimension().location().toString(),
                center,
                boundsOf(planned),
                candidate.shapeSeed(),
                candidate.zone(),
                candidate.resource(),
                resourceCycleId,
                candidate.reserve(),
                candidate.reserve(),
                System.currentTimeMillis(),
                0L,
                DepositState.GENERATING,
                planned,
                0,
                new HashSet<>()
        );
        oreDeposits.put(id, deposit);
        depositCenters.put(candidateChunk.toLong(), id);
        enqueueUnique(generationQueue, id);
        setDirty();
    }

    private static ResourceDistributionConfig distributionConfig() {
        int blue = ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt();
        int yellow = Math.max(blue, ModConfigSpec.RESOURCE_YELLOW_RADIUS.getAsInt());
        int minReserve = ModConfigSpec.RESOURCE_MIN_RESERVE.getAsInt();
        int maxReserve = Math.max(minReserve, ModConfigSpec.RESOURCE_MAX_RESERVE.getAsInt());
        int maxPhysical = ModConfigSpec.RESOURCE_MAX_PHYSICAL_BLOCKS.getAsInt();
        return new ResourceDistributionConfig(
                blue,
                yellow,
                ModConfigSpec.RESOURCE_CELL_SIZE.getAsInt(),
                ModConfigSpec.RESOURCE_BASE_DENSITY.getAsDouble(),
                minReserve,
                maxReserve,
                Math.max(maxReserve, (int) Math.ceil(maxReserve * 1.5D)),
                20,
                Math.min(80, maxPhysical),
                maxPhysical
        );
    }

    private static int chooseDepth(ServerLevel level, ResourceDistribution.CellCandidate candidate) {
        int[] range = depthRange(candidate.resource());
        int min = range[0];
        int max = range[1];
        if (candidate.zone() == ResourceZone.BLUE) {
            min = Math.max(min, -16);
            max += 20;
        } else if (candidate.zone() == ResourceZone.YELLOW) {
            min = Math.max(min, -40);
            max += 8;
        }
        min = Math.max(min, level.getMinBuildHeight() + 6);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, candidate.blockX(), candidate.blockZ());
        max = Math.min(max, Math.min(level.getMaxBuildHeight() - 8, surface - 8));
        if (max < min) {
            max = min;
        }
        int span = max - min + 1;
        return min + unsignedMod(mix64(candidate.shapeSeed() ^ 0xC6BC279692B5CC83L), span);
    }

    private static int[] depthRange(ClusterResource resource) {
        return switch (resource) {
            case COAL -> new int[]{-24, 72};
            case COPPER -> new int[]{-32, 56};
            case ZINC -> new int[]{-40, 48};
            case IRON -> new int[]{-56, 56};
            case LAPIS -> new int[]{-56, 24};
            case REDSTONE -> new int[]{-60, 0};
            case GOLD -> new int[]{-60, 20};
            case DIAMOND -> new int[]{-60, -8};
        };
    }

    private void ensureCluster(ServerLevel level, ChunkPos loadedChunk) {
        Plan plan = plan(level, loadedChunk);
        if (plan == null || !plan.chunk().equals(loadedChunk)) {
            return;
        }

        long chunkKey = loadedChunk.toLong();
        if (removedChunks.contains(chunkKey)) {
            return;
        }
        ResourceCluster cluster = clusters.get(chunkKey);
        if (cluster == null) {
            int surfaceY = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    plan.blockX(),
                    plan.blockZ()
            );
            int baseY = Math.clamp(
                    surfaceY,
                    level.getMinBuildHeight() + 1,
                    level.getMaxBuildHeight() - 4
            );
            UUID clusterId = namedUuid(
                    level.dimension().location() + ":" + plan.cellX() + ":" + plan.cellZ()
            );
            cluster = new ResourceCluster(
                    clusterId,
                    new BlockPos(plan.blockX(), baseY, plan.blockZ()),
                    plan.type(),
                    plan.richness(),
                    namedUuid(clusterId + ":" + ITEM_ROLE),
                    namedUuid(clusterId + ":" + TEXT_ROLE)
            );
            clusters.put(chunkKey, cluster);
            setDirty();
        }

        ensureBlocks(level, cluster);
        ensureDisplays(level, cluster);
        activeChunks.add(chunkKey);
    }

    private void processGeneration(ServerLevel level) {
        int budget = ModConfigSpec.RESOURCE_GENERATION_BLOCKS_PER_TICK.getAsInt();
        int visits = generationQueue.size();
        while (budget > 0 && visits-- > 0 && !generationQueue.isEmpty()) {
            UUID id = generationQueue.removeFirst();
            OreDeposit deposit = oreDeposits.get(id);
            if (deposit == null || deposit.state != DepositState.GENERATING) {
                continue;
            }
            ChunkPos chunk = new ChunkPos(deposit.center);
            if (!level.hasChunk(chunk.x, chunk.z)) {
                generationQueue.addLast(id);
                continue;
            }
            while (budget > 0 && deposit.generationIndex < deposit.plannedPositions.size()) {
                long posLong = deposit.plannedPositions.get(deposit.generationIndex++);
                BlockPos pos = BlockPos.of(posLong);
                if (canReplaceForDeposit(level, pos)) {
                    Block ore = oreBlock(deposit.resource, pos.getY());
                    if (ore != Blocks.AIR && level.setBlock(pos, ore.defaultBlockState(), 2)) {
                        deposit.createdPositions.add(posLong);
                        trackedOre.put(posLong, deposit.id);
                    }
                }
                budget--;
            }
            if (deposit.generationIndex >= deposit.plannedPositions.size()) {
                deposit.state = deposit.remaining > 0 ? DepositState.ACTIVE : DepositState.DEPLETED;
            } else {
                generationQueue.addLast(id);
            }
            setDirty();
        }
    }

    private void processCleanup(ServerLevel level) {
        int budget = ModConfigSpec.RESOURCE_CLEANUP_BLOCKS_PER_TICK.getAsInt();
        int visits = cleanupQueue.size();
        while (budget > 0 && visits-- > 0 && !cleanupQueue.isEmpty()) {
            UUID id = cleanupQueue.removeFirst();
            OreDeposit deposit = oreDeposits.get(id);
            if (deposit == null || deposit.state != DepositState.CLEANING) {
                continue;
            }
            boolean deferred = false;
            var iterator = deposit.createdPositions.iterator();
            while (budget > 0 && iterator.hasNext()) {
                long posLong = iterator.next();
                BlockPos pos = BlockPos.of(posLong);
                ChunkPos chunk = new ChunkPos(pos);
                if (!level.hasChunk(chunk.x, chunk.z)) {
                    deferred = true;
                    continue;
                }
                Block expected = oreBlock(deposit.resource, pos.getY());
                if (expected != Blocks.AIR && level.getBlockState(pos).is(expected)) {
                    level.setBlock(pos, naturalBase(pos.getY()).defaultBlockState(), 2);
                }
                trackedOre.remove(posLong);
                iterator.remove();
                budget--;
            }
            if (deposit.createdPositions.isEmpty()) {
                deposit.state = DepositState.CLEANED;
                depositCenters.remove(new ChunkPos(deposit.center).toLong(), deposit.id);
                queue(new ChunkPos(deposit.center), level.getGameTime());
            } else {
                cleanupQueue.addLast(id);
                if (deferred && budget > 0) {
                    budget--;
                }
            }
            setDirty();
        }
    }

    private static boolean canReplaceForDeposit(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) == null
                && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos).is(BlockTags.BASE_STONE_OVERWORLD);
    }

    private static Block naturalBase(int y) {
        return y < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
    }

    private static Block oreBlock(ClusterResource resource, int y) {
        boolean deep = y < 0;
        return switch (resource) {
            case COAL -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
            case COPPER -> deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
            case IRON -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
            case LAPIS -> deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
            case REDSTONE -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
            case GOLD -> deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
            case DIAMOND -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
            case ZINC -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                    "create",
                    deep ? "deepslate_zinc_ore" : "zinc_ore"
            ));
        };
    }

    private static List<Long> planShape(BlockPos center, int desired, long seed, ChunkPos chunk) {
        int target = Math.max(1, desired);
        Set<Long> positions = new java.util.LinkedHashSet<>();
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        int lobes = 2 + random.nextInt(3);
        int attempts = Math.max(256, target * 32);
        for (int attempt = 0; attempt < attempts && positions.size() < target; attempt++) {
            int lobe = attempt % lobes;
            double phase = lobe - (lobes - 1) / 2.0D;
            double cx = center.getX() + phase * 2.4D + random.nextDouble(-0.8D, 0.8D);
            double cy = center.getY() + phase * 0.9D + random.nextDouble(-0.5D, 0.5D);
            double cz = center.getZ() + Math.sin(lobe * 1.7D) * 2.0D + random.nextDouble(-0.8D, 0.8D);
            double radiusX = 2.0D + random.nextDouble(2.5D);
            double radiusY = 1.2D + random.nextDouble(1.8D);
            double radiusZ = 1.8D + random.nextDouble(2.8D);
            int x = (int) Math.round(cx + random.nextGaussian() * radiusX * 0.55D);
            int y = (int) Math.round(cy + random.nextGaussian() * radiusY * 0.55D);
            int z = (int) Math.round(cz + random.nextGaussian() * radiusZ * 0.55D);
            if ((x >> 4) == chunk.x && (z >> 4) == chunk.z) {
                positions.add(BlockPos.asLong(x, y, z));
            }
        }
        return List.copyOf(positions);
    }

    private static BoundingBoxData boundsOf(List<Long> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long value : positions) {
            BlockPos pos = BlockPos.of(value);
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new BoundingBoxData(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void markDepleted(OreDeposit deposit, long nowMillis) {
        if (deposit.depletedAt == 0L) {
            deposit.depletedAt = nowMillis;
        }
        if (deposit.state != DepositState.CLEANING && deposit.state != DepositState.CLEANED) {
            deposit.state = DepositState.DEPLETED;
        }
    }

    private static void enqueueUnique(Deque<UUID> queue, UUID id) {
        if (!queue.contains(id)) {
            queue.addLast(id);
        }
    }

    private void ensureBlocks(ServerLevel level, ResourceCluster cluster) {
        for (int offset = 0; offset < 3; offset++) {
            BlockPos pos = cluster.basePos().above(offset);
            if (!level.getBlockState(pos).is(cluster.type().block())) {
                level.setBlockAndUpdate(pos, cluster.type().block().defaultBlockState());
            }
        }
    }

    private void ensureDisplays(ServerLevel level, ResourceCluster cluster) {
        Display.ItemDisplay itemDisplay = findItemDisplay(level, cluster);
        if (itemDisplay == null) {
            itemDisplay = createItemDisplay(level, cluster);
        }
        if (itemDisplay != null && !itemDisplay.getUUID().equals(cluster.itemDisplayId())) {
            cluster.setItemDisplayId(itemDisplay.getUUID());
            setDirty();
        }

        Display.TextDisplay textDisplay = findTextDisplay(level, cluster);
        if (textDisplay == null) {
            textDisplay = createTextDisplay(level, cluster);
        }
        if (textDisplay != null && !textDisplay.getUUID().equals(cluster.textDisplayId())) {
            cluster.setTextDisplayId(textDisplay.getUUID());
            setDirty();
        }
    }

    private Display.ItemDisplay findItemDisplay(ServerLevel level, ResourceCluster cluster) {
        Entity byId = level.getEntity(cluster.itemDisplayId());
        Display.ItemDisplay selected = byId instanceof Display.ItemDisplay item
                && belongsTo(item, cluster, ITEM_ROLE) ? item : null;
        List<Display.ItemDisplay> matches = level.getEntitiesOfClass(
                Display.ItemDisplay.class,
                displaySearchBox(cluster),
                entity -> belongsTo(entity, cluster, ITEM_ROLE)
        );
        for (Display.ItemDisplay match : matches) {
            if (selected == null) {
                selected = match;
            } else if (selected != match) {
                match.discard();
            }
        }
        return selected;
    }

    private Display.TextDisplay findTextDisplay(ServerLevel level, ResourceCluster cluster) {
        Entity byId = level.getEntity(cluster.textDisplayId());
        Display.TextDisplay selected = byId instanceof Display.TextDisplay text
                && belongsTo(text, cluster, TEXT_ROLE) ? text : null;
        List<Display.TextDisplay> matches = level.getEntitiesOfClass(
                Display.TextDisplay.class,
                displaySearchBox(cluster),
                entity -> belongsTo(entity, cluster, TEXT_ROLE)
        );
        for (Display.TextDisplay match : matches) {
            if (selected == null) {
                selected = match;
            } else if (selected != match) {
                match.discard();
            }
        }
        return selected;
    }

    private Display.ItemDisplay createItemDisplay(ServerLevel level, ResourceCluster cluster) {
        Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.setUUID(cluster.itemDisplayId());
        configureBaseDisplay(display, cluster, ITEM_ROLE, 3.65D);
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString(Display.TAG_BILLBOARD, "horizontal");
        tag.putString("item_display", ItemDisplayContext.FIXED.getSerializedName());
        tag.putFloat(Display.TAG_VIEW_RANGE, 1.5F);
        display.load(tag);
        display.getSlot(0).set(new ItemStack(cluster.type().displayItem()));
        return level.addFreshEntity(display) ? display : existingItemById(level, cluster);
    }

    private Display.TextDisplay createTextDisplay(ServerLevel level, ResourceCluster cluster) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) {
            return null;
        }
        display.setUUID(cluster.textDisplayId());
        configureBaseDisplay(display, cluster, TEXT_ROLE, 4.65D);
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString(Display.TAG_BILLBOARD, "center");
        tag.putFloat(Display.TAG_VIEW_RANGE, 1.5F);
        tag.putInt("line_width", 160);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putInt("background", 0x60000000);
        Component text = Component.literal(cluster.type().displayName());
        tag.putString(Display.TextDisplay.TAG_TEXT, Component.Serializer.toJson(text, level.registryAccess()));
        display.load(tag);
        return level.addFreshEntity(display) ? display : existingTextById(level, cluster);
    }

    private void configureBaseDisplay(
            Display display,
            ResourceCluster cluster,
            String role,
            double verticalOffset
    ) {
        BlockPos base = cluster.basePos();
        display.moveTo(
                base.getX() + 0.5D,
                base.getY() + verticalOffset,
                base.getZ() + 0.5D,
                0.0F,
                0.0F
        );
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.getPersistentData().putString(ENTITY_CLUSTER_KEY, cluster.id().toString());
        display.getPersistentData().putString(ENTITY_ROLE_KEY, role);
    }

    private Display.ItemDisplay existingItemById(ServerLevel level, ResourceCluster cluster) {
        Entity entity = level.getEntity(cluster.itemDisplayId());
        return entity instanceof Display.ItemDisplay item ? item : null;
    }

    private Display.TextDisplay existingTextById(ServerLevel level, ResourceCluster cluster) {
        Entity entity = level.getEntity(cluster.textDisplayId());
        return entity instanceof Display.TextDisplay text ? text : null;
    }

    private void rotateItems(ServerLevel level) {
        activeChunks.removeIf(key -> {
            ChunkPos chunkPos = new ChunkPos(key);
            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                return true;
            }
            ResourceCluster cluster = clusters.get(key);
            if (cluster == null) {
                return true;
            }
            Entity entity = level.getEntity(cluster.itemDisplayId());
            if (entity instanceof Display.ItemDisplay display && belongsTo(display, cluster, ITEM_ROLE)) {
                display.yRotO = display.getYRot();
                display.setYRot((display.getYRot() + 1.0F) % 360.0F);
            }
            return false;
        });
    }

    private void repairActive(ServerLevel level) {
        List<Long> loaded = new ArrayList<>(activeChunks);
        for (Long key : loaded) {
            ChunkPos chunkPos = new ChunkPos(key);
            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                activeChunks.remove(key);
                continue;
            }
            ResourceCluster cluster = clusters.get(key);
            if (cluster != null) {
                ensureBlocks(level, cluster);
                ensureDisplays(level, cluster);
            }
        }
    }

    private static boolean belongsTo(Entity entity, ResourceCluster cluster, String role) {
        CompoundTag data = entity.getPersistentData();
        return cluster.id().toString().equals(data.getString(ENTITY_CLUSTER_KEY))
                && role.equals(data.getString(ENTITY_ROLE_KEY));
    }

    private static AABB displaySearchBox(ResourceCluster cluster) {
        BlockPos pos = cluster.basePos();
        return new AABB(
                pos.getX() - 1.0D,
                pos.getY() - 1.0D,
                pos.getZ() - 1.0D,
                pos.getX() + 2.0D,
                pos.getY() + 7.0D,
                pos.getZ() + 2.0D
        );
    }

    private static Plan plan(ServerLevel level, ChunkPos loadedChunk) {
        int cellX = Math.floorDiv(loadedChunk.x, CELL_SIZE_CHUNKS);
        int cellZ = Math.floorDiv(loadedChunk.z, CELL_SIZE_CHUNKS);
        long seed = mix64(
                level.getSeed()
                        ^ ((long) cellX * CELL_X_SALT)
                        ^ ((long) cellZ * CELL_Z_SALT)
                        ^ level.dimension().location().hashCode()
        );
        if (unsignedMod(seed, 4) != 0) {
            return null;
        }

        int chunkX = cellX * CELL_SIZE_CHUNKS + unsignedMod(mix64(seed + GOLDEN_GAMMA), CELL_SIZE_CHUNKS);
        int chunkZ = cellZ * CELL_SIZE_CHUNKS + unsignedMod(mix64(seed + GOLDEN_GAMMA * 2L), CELL_SIZE_CHUNKS);
        int localX = 2 + unsignedMod(mix64(seed + GOLDEN_GAMMA * 3L), 12);
        int localZ = 2 + unsignedMod(mix64(seed + GOLDEN_GAMMA * 4L), 12);
        ResourceClusterType type = ResourceClusterType.weighted(
                unsignedMod(mix64(seed + GOLDEN_GAMMA * 5L), 100)
        );
        int richness = 1 + unsignedMod(mix64(seed + GOLDEN_GAMMA * 6L), 3);
        return new Plan(
                cellX,
                cellZ,
                new ChunkPos(chunkX, chunkZ),
                (chunkX << 4) + localX,
                (chunkZ << 4) + localZ,
                type,
                richness
        );
    }

    private static int unsignedMod(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static UUID namedUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        ListTag list = new ListTag();
        for (ResourceCluster cluster : clusters.values()) {
            list.add(cluster.save());
        }
        tag.put(TAG_CLUSTERS, list);
        tag.putLongArray(TAG_REMOVED, removedChunks.stream().mapToLong(Long::longValue).toArray());
        ListTag deposits = new ListTag();
        for (OreDeposit deposit : oreDeposits.values()) {
            deposits.add(deposit.save());
        }
        tag.put(TAG_DEPOSITS, deposits);
        tag.putLong(TAG_CYCLE, resourceCycleId);
        tag.putLong(TAG_NEXT_CYCLE, nextResourceCycleMillis);
        tag.putBoolean(TAG_PAUSED, resourceQueuePaused);
        return tag;
    }

    private static ResourceClusterManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceClusterManager manager = new ResourceClusterManager();
        ListTag list = tag.getList(TAG_CLUSTERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            Optional<ResourceCluster> loaded = ResourceCluster.load(list.getCompound(index));
            if (loaded.isEmpty()) {
                LOGGER.warn("Skipped invalid resource cluster at NBT index {}", index);
                manager.setDirty();
                continue;
            }
            ResourceCluster cluster = loaded.get();
            long key = new ChunkPos(cluster.basePos()).toLong();
            if (manager.clusters.putIfAbsent(key, cluster) != null) {
                LOGGER.warn("Skipped duplicate resource cluster in chunk {}", new ChunkPos(key));
                manager.setDirty();
            }
        }
        for (long key : tag.getLongArray(TAG_REMOVED)) {
            manager.removedChunks.add(key);
        }
        manager.resourceCycleId = Math.max(0L, tag.getLong(TAG_CYCLE));
        manager.nextResourceCycleMillis = Math.max(0L, tag.getLong(TAG_NEXT_CYCLE));
        manager.resourceQueuePaused = tag.getBoolean(TAG_PAUSED);
        ListTag deposits = tag.getList(TAG_DEPOSITS, Tag.TAG_COMPOUND);
        for (int index = 0; index < deposits.size(); index++) {
            Optional<OreDeposit> loaded = OreDeposit.load(deposits.getCompound(index));
            if (loaded.isEmpty()) {
                LOGGER.warn("Skipped invalid ore deposit at NBT index {}", index);
                manager.setDirty();
                continue;
            }
            OreDeposit deposit = loaded.get();
            if (manager.oreDeposits.putIfAbsent(deposit.id, deposit) != null) {
                LOGGER.warn("Skipped duplicate ore deposit {}", deposit.id);
                manager.setDirty();
                continue;
            }
            long centerChunk = new ChunkPos(deposit.center).toLong();
            if (deposit.state != DepositState.CLEANED) {
                manager.depositCenters.putIfAbsent(centerChunk, deposit.id);
            }
            for (long pos : deposit.createdPositions) {
                manager.trackedOre.putIfAbsent(pos, deposit.id);
            }
            if (deposit.state == DepositState.GENERATING) {
                manager.generationQueue.addLast(deposit.id);
            } else if (deposit.state == DepositState.CLEANING) {
                manager.cleanupQueue.addLast(deposit.id);
            }
        }
        return manager;
    }

    public record ClusterView(ResourceClusterType type, int richness) {
    }

    public record OreDepositView(
            UUID id,
            ClusterResource resource,
            String dimension,
            BlockPos center,
            ResourceZone zone,
            long cycleId,
            int originalReserve,
            int remaining,
            long createdAt,
            long depletedAt,
            String state
    ) {
    }

    public record DrillExtraction(
            ClusterResource resource,
            int amount,
            int remaining,
            int originalReserve
    ) {
        private static DrillExtraction empty() {
            return new DrillExtraction(null, 0, 0, 0);
        }

        public boolean successful() {
            return resource != null && amount > 0;
        }
    }

    public record ResourceStatistics(
            long cycleId,
            int active,
            int depleted,
            Map<ResourceZone, Integer> byZone,
            Map<ClusterResource, Integer> byResource
    ) {
    }

    public record IntegrityReport(int deposits, int trackedBlocks, int generationQueued, int cleanupQueued, int issues) {
    }

    public enum OreConsumption {
        NOT_TRACKED,
        CHARGED,
        DEPLETED_NO_DROP
    }

    private record Plan(
            int cellX,
            int cellZ,
            ChunkPos chunk,
            int blockX,
            int blockZ,
            ResourceClusterType type,
            int richness
    ) {
    }

    private enum DepositState {
        GENERATING,
        ACTIVE,
        DEPLETED,
        CLEANING,
        CLEANED
    }

    private record BoundingBoxData(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("minX", minX);
            tag.putInt("minY", minY);
            tag.putInt("minZ", minZ);
            tag.putInt("maxX", maxX);
            tag.putInt("maxY", maxY);
            tag.putInt("maxZ", maxZ);
            return tag;
        }

        private static Optional<BoundingBoxData> load(CompoundTag tag) {
            int minX = tag.getInt("minX");
            int minY = tag.getInt("minY");
            int minZ = tag.getInt("minZ");
            int maxX = tag.getInt("maxX");
            int maxY = tag.getInt("maxY");
            int maxZ = tag.getInt("maxZ");
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                return Optional.empty();
            }
            return Optional.of(new BoundingBoxData(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static final class OreDeposit {
        private final UUID id;
        private final String dimension;
        private final BlockPos center;
        private final BoundingBoxData bounds;
        private final long shapeSeed;
        private final ResourceZone zone;
        private final ClusterResource resource;
        private final long cycleId;
        private final int originalReserve;
        private int remaining;
        private final long createdAt;
        private long depletedAt;
        private DepositState state;
        private final List<Long> plannedPositions;
        private int generationIndex;
        private final Set<Long> createdPositions;

        private OreDeposit(
                UUID id,
                String dimension,
                BlockPos center,
                BoundingBoxData bounds,
                long shapeSeed,
                ResourceZone zone,
                ClusterResource resource,
                long cycleId,
                int originalReserve,
                int remaining,
                long createdAt,
                long depletedAt,
                DepositState state,
                List<Long> plannedPositions,
                int generationIndex,
                Set<Long> createdPositions
        ) {
            this.id = id;
            this.dimension = dimension;
            this.center = center.immutable();
            this.bounds = bounds;
            this.shapeSeed = shapeSeed;
            this.zone = zone;
            this.resource = resource;
            this.cycleId = cycleId;
            this.originalReserve = originalReserve;
            this.remaining = remaining;
            this.createdAt = createdAt;
            this.depletedAt = depletedAt;
            this.state = state;
            this.plannedPositions = new ArrayList<>(plannedPositions);
            this.generationIndex = generationIndex;
            this.createdPositions = new HashSet<>(createdPositions);
        }

        private OreDepositView view() {
            return new OreDepositView(
                    id,
                    resource,
                    dimension,
                    center,
                    zone,
                    cycleId,
                    originalReserve,
                    remaining,
                    createdAt,
                    depletedAt,
                    state.name().toLowerCase(java.util.Locale.ROOT)
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("dimension", dimension);
            tag.put("center", NbtUtils.writeBlockPos(center));
            tag.put("bounds", bounds.save());
            tag.putLong("shapeSeed", shapeSeed);
            tag.putString("zone", zone.name());
            tag.putString("resource", resource.id());
            tag.putLong("cycleId", cycleId);
            tag.putInt("originalReserve", originalReserve);
            tag.putInt("remaining", remaining);
            tag.putLong("createdAt", createdAt);
            tag.putLong("depletedAt", depletedAt);
            tag.putString("state", state.name());
            tag.putLongArray("plannedPositions", plannedPositions.stream().mapToLong(Long::longValue).toArray());
            tag.putInt("generationIndex", generationIndex);
            tag.putLongArray("createdPositions", createdPositions.stream().mapToLong(Long::longValue).toArray());
            return tag;
        }

        private static Optional<OreDeposit> load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.contains("center", Tag.TAG_COMPOUND)
                    || !tag.contains("bounds", Tag.TAG_COMPOUND)) {
                return Optional.empty();
            }
            Optional<BlockPos> center = NbtUtils.readBlockPos(tag, "center");
            Optional<BoundingBoxData> bounds = BoundingBoxData.load(tag.getCompound("bounds"));
            Optional<ClusterResource> resource = ClusterResource.parse(tag.getString("resource"));
            ResourceZone zone;
            DepositState state;
            try {
                zone = ResourceZone.valueOf(tag.getString("zone"));
                state = DepositState.valueOf(tag.getString("state"));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
            int original = tag.getInt("originalReserve");
            int remaining = tag.getInt("remaining");
            long cycle = tag.getLong("cycleId");
            long createdAt = tag.getLong("createdAt");
            if (center.isEmpty() || bounds.isEmpty() || resource.isEmpty() || original <= 0
                    || remaining < 0 || remaining > original || cycle < 0L || createdAt <= 0L) {
                return Optional.empty();
            }
            long[] plannedArray = tag.getLongArray("plannedPositions");
            long[] createdArray = tag.getLongArray("createdPositions");
            if (plannedArray.length > 4096 || createdArray.length > 4096) {
                return Optional.empty();
            }
            List<Long> planned = java.util.Arrays.stream(plannedArray).boxed().toList();
            Set<Long> created = new HashSet<>();
            java.util.Arrays.stream(createdArray).forEach(created::add);
            int index = Math.clamp(tag.getInt("generationIndex"), 0, planned.size());
            return Optional.of(new OreDeposit(
                    tag.getUUID("id"),
                    tag.getString("dimension"),
                    center.get(),
                    bounds.get(),
                    tag.getLong("shapeSeed"),
                    zone,
                    resource.get(),
                    cycle,
                    original,
                    remaining,
                    createdAt,
                    Math.max(0L, tag.getLong("depletedAt")),
                    state,
                    planned,
                    index,
                    created
            ));
        }
    }

    private static final class ResourceCluster {
        private static final String TAG_ID = "id";
        private static final String TAG_BASE_POS = "basePos";
        private static final String TAG_TYPE = "type";
        private static final String TAG_RICHNESS = "richness";
        private static final String TAG_ITEM_DISPLAY = "itemDisplay";
        private static final String TAG_TEXT_DISPLAY = "textDisplay";

        private final UUID id;
        private final BlockPos basePos;
        private final ResourceClusterType type;
        private final int richness;
        private UUID itemDisplayId;
        private UUID textDisplayId;

        private ResourceCluster(
                UUID id,
                BlockPos basePos,
                ResourceClusterType type,
                int richness,
                UUID itemDisplayId,
                UUID textDisplayId
        ) {
            this.id = id;
            this.basePos = basePos.immutable();
            this.type = type;
            this.richness = richness;
            this.itemDisplayId = itemDisplayId;
            this.textDisplayId = textDisplayId;
        }

        private UUID id() {
            return id;
        }

        private BlockPos basePos() {
            return basePos;
        }

        private ResourceClusterType type() {
            return type;
        }

        private int richness() {
            return richness;
        }

        private UUID itemDisplayId() {
            return itemDisplayId;
        }

        private void setItemDisplayId(UUID itemDisplayId) {
            this.itemDisplayId = itemDisplayId;
        }

        private UUID textDisplayId() {
            return textDisplayId;
        }

        private void setTextDisplayId(UUID textDisplayId) {
            this.textDisplayId = textDisplayId;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, id);
            tag.put(TAG_BASE_POS, NbtUtils.writeBlockPos(basePos));
            tag.putString(TAG_TYPE, type.id());
            tag.putInt(TAG_RICHNESS, richness);
            tag.putUUID(TAG_ITEM_DISPLAY, itemDisplayId);
            tag.putUUID(TAG_TEXT_DISPLAY, textDisplayId);
            return tag;
        }

        private static Optional<ResourceCluster> load(CompoundTag tag) {
            if (!tag.hasUUID(TAG_ID)
                    || !tag.hasUUID(TAG_ITEM_DISPLAY)
                    || !tag.hasUUID(TAG_TEXT_DISPLAY)) {
                return Optional.empty();
            }
            Optional<BlockPos> basePos = NbtUtils.readBlockPos(tag, TAG_BASE_POS);
            Optional<ResourceClusterType> type = ResourceClusterType.parse(tag.getString(TAG_TYPE));
            int richness = tag.getInt(TAG_RICHNESS);
            if (basePos.isEmpty() || type.isEmpty() || richness < 1 || richness > 3) {
                return Optional.empty();
            }
            return Optional.of(new ResourceCluster(
                    tag.getUUID(TAG_ID),
                    basePos.get(),
                    type.get(),
                    richness,
                    tag.getUUID(TAG_ITEM_DISPLAY),
                    tag.getUUID(TAG_TEXT_DISPLAY)
            ));
        }
    }

    private ResourceClusterManager() {
    }
}
