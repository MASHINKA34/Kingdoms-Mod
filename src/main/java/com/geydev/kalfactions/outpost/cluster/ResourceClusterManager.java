package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.outpost.cluster.distribution.SurfaceClusterDistribution;
import com.geydev.kalfactions.worldgen.ZonedOreFeature;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class ResourceClusterManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_resource_clusters";
    public static final Factory<ResourceClusterManager> FACTORY =
            new Factory<>(ResourceClusterManager::new, ResourceClusterManager::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LOAD_DELAY_TICKS = 1;
    private static final int MAX_PLACEMENTS_PER_TICK = 16;
    private static final String TAG_CLUSTERS = "clusters";
    private static final String TAG_REMOVED = "removed";
    private static final int DATA_VERSION = 5;
    private static final String TAG_VERSION = "formatVersion";
    private static final String TAG_DRILL_BINDINGS = "drillBindings";
    private static final String TAG_PENDING_CHUNKS = "pendingChunks";
    private static final String TAG_GENERATION = "generation";
    private static final String TAG_STALE = "staleClusters";
    private static final String ENTITY_CLUSTER_KEY = KalFactions.MOD_ID + "ResourceCluster";
    private static final String ENTITY_ROLE_KEY = KalFactions.MOD_ID + "ResourceClusterRole";
    private static final String ITEM_ROLE = "item";
    private static final String TEXT_ROLE = "text";
    private static final long GENERATION_SALT = 0x9E3779B97F4A7C15L;
    private static final int MANUAL_UNITS_PER_BLOCK = 1;
    private static final int RESET_SCAN_INTERVAL_TICKS = 20;
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final Map<Long, ResourceCluster> clusters = new LinkedHashMap<>();
    private final Map<Long, Long> pendingChunks = new HashMap<>();
    private final Set<Long> activeChunks = new HashSet<>();
    private final Map<Long, Long> boundDrill = new HashMap<>();
    private final Set<Long> removedChunks = new HashSet<>();
    private final Map<Long, List<ResourceCluster>> staleClusters = new LinkedHashMap<>();
    private final Set<Long> runningTimers = new HashSet<>();
    private int generation;
    private int sinceResetScan;

    public static ResourceClusterManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Optional<ClusterView> clusterAt(ChunkPos chunkPos) {
        ResourceCluster cluster = clusters.get(chunkPos.toLong());
        return cluster == null
                ? Optional.empty()
                : Optional.of(new ClusterView(cluster.basePos(), cluster.type(), cluster.richness()));
    }

    public synchronized List<SurfaceClusterView> clustersIn(Set<ClaimKey> claims, ResourceLocation dimension) {
        List<SurfaceClusterView> result = new ArrayList<>();
        for (Map.Entry<Long, ResourceCluster> entry : clusters.entrySet()) {
            ChunkPos chunk = new ChunkPos(entry.getKey());
            ClaimKey key = new ClaimKey(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            dimension
                    ),
                    chunk
            );
            if (!claims.contains(key)) {
                continue;
            }
            ResourceCluster cluster = entry.getValue();
            result.add(new SurfaceClusterView(
                    entry.getKey(),
                    cluster.basePos(),
                    cluster.type(),
                    cluster.richness(),
                    boundDrill.get(entry.getKey()),
                    reserveOf(cluster)
            ));
        }
        result.sort(java.util.Comparator.comparingLong(SurfaceClusterView::chunk));
        return List.copyOf(result);
    }

    public synchronized Optional<ReserveView> reserveAt(ChunkPos chunkPos) {
        ResourceCluster cluster = clusters.get(chunkPos.toLong());
        return cluster == null ? Optional.empty() : Optional.of(reserveOf(cluster));
    }

    public synchronized int consume(ServerLevel level, ChunkPos chunkPos, int units) {
        if (units <= 0) {
            return 0;
        }
        long key = chunkPos.toLong();
        ResourceCluster cluster = clusters.get(key);
        if (cluster == null) {
            return 0;
        }
        int limit = limitFor(cluster.richness());
        int granted = Math.min(units, Math.max(0, limit - cluster.spent()));
        if (granted <= 0) {
            exhaust(level, cluster);
            return 0;
        }
        if (cluster.firstExtractionMillis() == 0L) {
            cluster.setFirstExtractionMillis(ClusterClock.now());
            runningTimers.add(key);
        }
        cluster.setSpent(cluster.spent() + granted);
        if (cluster.spent() >= limit) {
            exhaust(level, cluster);
        } else {
            refreshTextDisplay(level, cluster);
        }
        setDirty();
        return granted;
    }

    public synchronized int drain(ServerLevel level, ChunkPos chunkPos) {
        ResourceCluster cluster = clusters.get(chunkPos.toLong());
        if (cluster == null) {
            return 0;
        }
        int remaining = Math.max(0, limitFor(cluster.richness()) - cluster.spent());
        if (remaining > 0) {
            return consume(level, chunkPos, remaining);
        }
        exhaust(level, cluster);
        return 0;
    }

    public synchronized int consumeMinedBlock(ServerLevel level, BlockPos pos) {
        ResourceCluster cluster = clusters.get(ChunkPos.asLong(pos));
        if (cluster == null || !isClusterColumn(cluster, pos)) {
            return 0;
        }
        return consume(level, new ChunkPos(pos), MANUAL_UNITS_PER_BLOCK);
    }

    public static int limitFor(int richness) {
        return switch (Math.clamp(richness, 1, 3)) {
            case 1 -> ModConfigSpec.CLUSTER_UNITS_RICHNESS_1.getAsInt();
            case 2 -> ModConfigSpec.CLUSTER_UNITS_RICHNESS_2.getAsInt();
            default -> ModConfigSpec.CLUSTER_UNITS_RICHNESS_3.getAsInt();
        };
    }

    private static long resetMillis() {
        return ModConfigSpec.CLUSTER_RESET_DAYS.getAsInt() * DAY_MILLIS;
    }

    private static ReserveView reserveOf(ResourceCluster cluster) {
        int limit = limitFor(cluster.richness());
        long restoreIn = 0L;
        if (cluster.firstExtractionMillis() != 0L) {
            restoreIn = Math.max(
                    0L,
                    cluster.firstExtractionMillis() + resetMillis() - ClusterClock.now()
            );
        }
        return new ReserveView(
                Math.max(0, limit - cluster.spent()),
                limit,
                restoreIn,
                cluster.firstExtractionMillis() != 0L,
                cluster.exhausted()
        );
    }

    private void exhaust(ServerLevel level, ResourceCluster cluster) {
        if (!cluster.exhausted()) {
            cluster.setExhausted(true);
            setDirty();
        }
        if (level != null) {
            removeClusterBlocks(level, cluster);
            refreshTextDisplay(level, cluster);
        }
    }

    private void processResets(ServerLevel level, List<Long> restored) {
        long now = ClusterClock.now();
        long resetMillis = resetMillis();
        List<Long> due = new ArrayList<>();
        for (Long key : runningTimers) {
            ResourceCluster cluster = clusters.get(key);
            if (cluster == null
                    || cluster.firstExtractionMillis() == 0L
                    || cluster.firstExtractionMillis() + resetMillis <= now) {
                due.add(key);
            }
        }
        for (Long key : due) {
            ResourceCluster cluster = clusters.get(key);
            if (cluster == null) {
                runningTimers.remove(key);
                continue;
            }
            refill(level, cluster);
            restored.add(key);
        }
        if (!due.isEmpty()) {
            setDirty();
        }
    }

    private void refill(ServerLevel level, ResourceCluster cluster) {
        cluster.setSpent(0);
        cluster.setFirstExtractionMillis(0L);
        cluster.setExhausted(false);
        runningTimers.remove(new ChunkPos(cluster.basePos()).toLong());
        setDirty();
        ChunkPos chunkPos = new ChunkPos(cluster.basePos());
        if (level != null && level.hasChunk(chunkPos.x, chunkPos.z)) {
            ensureBlocks(level, cluster);
        }
        refreshTextDisplay(level, cluster);
    }

    public synchronized int pendingChunkCount() {
        return pendingChunks.size();
    }

    public synchronized ChunkDiagnostic diagnoseChunk(ServerLevel level, ChunkPos chunkPos) {
        BlockPos spawn = level.getSharedSpawnPos();
        ResourceZone zone = ZonedOreFeature.zoneAt(
                chunkPos.getMiddleBlockX(),
                chunkPos.getMiddleBlockZ(),
                spawn.getX(),
                spawn.getZ()
        );
        Plan surfacePlan = plan(level, chunkPos);
        ResourceCluster surface = clusters.get(chunkPos.toLong());
        String surfaceReason;
        if (surface != null) {
            surfaceReason = "cluster_active";
        } else if (zone == ResourceZone.BLUE) {
            surfaceReason = "blue_zone_disabled";
        } else if (zone == ResourceZone.BLACK) {
            surfaceReason = "black_zone_disabled";
        } else if (surfacePlan == null) {
            surfaceReason = "spacing_rejected";
        } else if (!surfacePlan.chunk().equals(chunkPos)) {
            surfaceReason = "candidate_in_other_chunk";
        } else if (pendingChunks.containsKey(chunkPos.toLong())) {
            surfaceReason = "pending";
        } else if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            surfaceReason = "chunk_not_loaded";
        } else {
            surfaceReason = "eligible_not_queued";
        }
        return new ChunkDiagnostic(
                chunkPos,
                zone,
                ZonedOreFeature.oreSizeSummary(zone),
                surfacePlan == null ? null : surfacePlan.chunk(),
                surfacePlan == null ? null : new BlockPos(
                        surfacePlan.blockX(),
                        level.hasChunk(surfacePlan.chunk().x, surfacePlan.chunk().z)
                                ? level.getHeight(
                                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                        surfacePlan.blockX(),
                                        surfacePlan.blockZ()
                                )
                                : level.getMinBuildHeight(),
                        surfacePlan.blockZ()
                ),
                surfacePlan == null ? null : surfacePlan.type(),
                surfaceReason,
                pendingChunks.size(),
                clusters.size()
        );
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
        removeClusterBlocks(level, cluster);
        removeClusterDisplays(level, cluster);
        clusters.remove(key);
        pendingChunks.remove(key);
        activeChunks.remove(key);
        boundDrill.remove(key);
        runningTimers.remove(key);
        removedChunks.add(key);
        setDirty();
        return Optional.of(cluster.type());
    }

    private void removeClusterBlocks(ServerLevel level, ResourceCluster cluster) {
        for (int offset = 0; offset < 3; offset++) {
            BlockPos blockPos = cluster.basePos().above(offset);
            if (level.getBlockState(blockPos).is(cluster.type().block())) {
                level.removeBlock(blockPos, false);
            }
        }
    }

    private void removeClusterDisplays(ServerLevel level, ResourceCluster cluster) {
        Display.ItemDisplay itemDisplay = findItemDisplay(level, cluster);
        if (itemDisplay != null) {
            itemDisplay.discard();
        }
        Display.TextDisplay textDisplay = findTextDisplay(level, cluster);
        if (textDisplay != null) {
            textDisplay.discard();
        }
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
            setDirty();
        }
        return true;
    }

    public synchronized void unbindDrill(ChunkPos chunkPos, BlockPos drillPos) {
        long key = chunkPos.toLong();
        Long existing = boundDrill.get(key);
        if (existing != null && existing == drillPos.asLong()) {
            boundDrill.remove(key);
            setDirty();
        }
    }

    public synchronized Optional<BlockPos> boundDrillAt(ChunkPos chunkPos) {
        Long existing = boundDrill.get(chunkPos.toLong());
        return existing == null ? Optional.empty() : Optional.of(BlockPos.of(existing));
    }

    public synchronized boolean isBoundDrill(ChunkPos chunkPos, BlockPos drillPos) {
        Long existing = boundDrill.get(chunkPos.toLong());
        return existing != null && existing == drillPos.asLong();
    }

    public synchronized void queue(ChunkPos chunkPos, long gameTime) {
        long key = chunkPos.toLong();
        long due = gameTime + LOAD_DELAY_TICKS;
        Long previous = pendingChunks.putIfAbsent(key, due);
        if (previous == null) {
            setDirty();
        } else if (due < previous) {
            pendingChunks.put(key, due);
            setDirty();
        }
    }

    public synchronized void deactivate(ChunkPos chunkPos) {
        long key = chunkPos.toLong();
        if (pendingChunks.remove(key) != null) {
            setDirty();
        }
        activeChunks.remove(key);
    }

    public synchronized void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        processPending(level, gameTime);
        List<Long> restored = new ArrayList<>();
        if (++sinceResetScan >= RESET_SCAN_INTERVAL_TICKS) {
            sinceResetScan = 0;
            if (!runningTimers.isEmpty()) {
                processResets(level, restored);
            }
        }
        if ((gameTime & 1L) == 0L) {
            rotateItems(level);
        }
        if (gameTime % 200L == 0L) {
            repairActive(level);
        }
        for (Long key : restored) {
            DrillService.notifyClusterChanged(level, new ChunkPos(key));
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
                clearStale(level, key);
                ensureCluster(level, chunkPos);
            }
        }
        if (!ready.isEmpty()) {
            setDirty();
        }
    }

    private void clearStale(ServerLevel level, long chunkKey) {
        List<ResourceCluster> stale = staleClusters.remove(chunkKey);
        if (stale == null) {
            return;
        }
        for (ResourceCluster cluster : stale) {
            removeClusterBlocks(level, cluster);
            removeClusterDisplays(level, cluster);
        }
        setDirty();
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
                            + (effectiveGeneration() == 0 ? "" : ":" + effectiveGeneration())
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

    private void ensureBlocks(ServerLevel level, ResourceCluster cluster) {
        if (cluster.exhausted()) {
            removeClusterBlocks(level, cluster);
            return;
        }
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
        } else {
            refreshTextDisplay(level, cluster);
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
        tag.putString(
                Display.TextDisplay.TAG_TEXT,
                Component.Serializer.toJson(displayText(cluster), level.registryAccess())
        );
        display.load(tag);
        return level.addFreshEntity(display) ? display : existingTextById(level, cluster);
    }

    private static Component displayText(ResourceCluster cluster) {
        ReserveView reserve = reserveOf(cluster);
        net.minecraft.network.chat.MutableComponent text = Component.literal(cluster.type().displayName())
                .append(Component.literal("\n"))
                .append(Component.translatable("kingdoms.cluster.display.richness", cluster.richness()));
        if (reserve.exhausted()) {
            text.append(Component.literal("\n")).append(Component.translatable(
                    "kingdoms.cluster.display.depleted",
                    displayDays(reserve.restoreInMillis()),
                    displayClock(reserve.restoreInMillis())
            ));
        }
        return text;
    }

    private static long displayDays(long millis) {
        return Math.max(0L, millis) / 1000L / 86400L;
    }

    private static String displayClock(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format("%02d:%02d", seconds % 86400L / 3600L, seconds % 3600L / 60L);
    }

    private void refreshTextDisplay(ServerLevel level, ResourceCluster cluster) {
        if (level == null) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(cluster.basePos());
        if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
            return;
        }
        Display.TextDisplay display = findTextDisplay(level, cluster);
        if (display == null) {
            return;
        }
        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString(
                Display.TextDisplay.TAG_TEXT,
                Component.Serializer.toJson(displayText(cluster), level.registryAccess())
        );
        display.load(tag);
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

    private Plan plan(ServerLevel level, ChunkPos loadedChunk) {
        int blue = ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt();
        int yellow = Math.max(blue, ModConfigSpec.RESOURCE_YELLOW_RADIUS.getAsInt());
        int red = Math.max(yellow, ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt());
        SurfaceClusterDistribution distribution = new SurfaceClusterDistribution(
                level.getSeed() ^ level.dimension().location().hashCode() ^ generationSalt(),
                level.getSharedSpawnPos().getX(),
                level.getSharedSpawnPos().getZ(),
                blue,
                yellow,
                red,
                ModConfigSpec.RESOURCE_YELLOW_CLUSTER_SPACING_CHUNKS.getAsInt(),
                ModConfigSpec.RESOURCE_RED_CLUSTER_SPACING_CHUNKS.getAsInt()
        );
        SurfaceClusterDistribution.Candidate candidate =
                distribution.candidateForChunk(loadedChunk.x, loadedChunk.z).orElse(null);
        if (candidate == null) {
            return null;
        }
        return new Plan(
                loadedChunk.x,
                loadedChunk.z,
                loadedChunk,
                loadedChunk.getMinBlockX() + candidate.localX(),
                loadedChunk.getMinBlockZ() + candidate.localZ(),
                ResourceClusterType.weighted(candidate.typeRoll()),
                candidate.richness()
        );
    }

    private static UUID namedUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized Set<Long> knownChunkKeys() {
        Set<Long> keys = new HashSet<>(clusters.keySet());
        keys.addAll(removedChunks);
        keys.addAll(staleClusters.keySet());
        return keys;
    }

    public synchronized MaintenancePreview preview(Set<Long> keys) {
        int touched = 0;
        int timers = 0;
        int exhausted = 0;
        for (Map.Entry<Long, ResourceCluster> entry : clusters.entrySet()) {
            if (keys != null && !keys.contains(entry.getKey())) {
                continue;
            }
            touched++;
            if (entry.getValue().firstExtractionMillis() != 0L) {
                timers++;
            }
            if (entry.getValue().exhausted()) {
                exhausted++;
            }
        }
        int tombstones = 0;
        for (Long key : removedChunks) {
            if (keys == null || keys.contains(key)) {
                tombstones++;
            }
        }
        return new MaintenancePreview(touched, timers, exhausted, tombstones, boundDrill.size());
    }

    public synchronized void resetChunk(ServerLevel level, long chunkKey) {
        removedChunks.remove(chunkKey);
        runningTimers.remove(chunkKey);
        ResourceCluster cluster = clusters.get(chunkKey);
        if (cluster != null) {
            refill(level, cluster);
        }
        ChunkPos chunkPos = new ChunkPos(chunkKey);
        if (level.hasChunk(chunkPos.x, chunkPos.z)) {
            clearStale(level, chunkKey);
            ensureCluster(level, chunkPos);
        }
        setDirty();
    }

    public synchronized int beginRegeneration() {
        generation++;
        for (Map.Entry<Long, ResourceCluster> entry : clusters.entrySet()) {
            staleClusters.computeIfAbsent(entry.getKey(), key -> new ArrayList<>()).add(entry.getValue());
        }
        int moved = clusters.size();
        clusters.clear();
        boundDrill.clear();
        removedChunks.clear();
        pendingChunks.clear();
        runningTimers.clear();
        activeChunks.clear();
        setDirty();
        return moved;
    }

    public synchronized int effectiveGeneration() {
        return ModConfigSpec.CLUSTER_GENERATION.getAsInt() + generation;
    }

    private long generationSalt() {
        return effectiveGeneration() * GENERATION_SALT;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_VERSION, DATA_VERSION);
        tag.putInt(TAG_GENERATION, generation);
        ListTag list = new ListTag();
        for (ResourceCluster cluster : clusters.values()) {
            list.add(cluster.save());
        }
        tag.put(TAG_CLUSTERS, list);
        ListTag stale = new ListTag();
        for (Map.Entry<Long, List<ResourceCluster>> entry : staleClusters.entrySet()) {
            for (ResourceCluster cluster : entry.getValue()) {
                CompoundTag staleTag = cluster.save();
                staleTag.putLong("chunk", entry.getKey());
                stale.add(staleTag);
            }
        }
        tag.put(TAG_STALE, stale);
        ListTag drillBindings = new ListTag();
        for (Map.Entry<Long, Long> binding : boundDrill.entrySet()) {
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putLong("clusterChunk", binding.getKey());
            bindingTag.putLong("drillPos", binding.getValue());
            drillBindings.add(bindingTag);
        }
        tag.put(TAG_DRILL_BINDINGS, drillBindings);
        tag.putLongArray(TAG_REMOVED, removedChunks.stream().mapToLong(Long::longValue).toArray());
        ListTag pending = new ListTag();
        for (Map.Entry<Long, Long> entry : pendingChunks.entrySet()) {
            CompoundTag pendingTag = new CompoundTag();
            pendingTag.putLong("chunk", entry.getKey());
            pendingTag.putLong("due", entry.getValue());
            pending.add(pendingTag);
        }
        tag.put(TAG_PENDING_CHUNKS, pending);
        return tag;
    }

    static ResourceClusterManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceClusterManager manager = new ResourceClusterManager();
        manager.generation = Math.max(0, tag.getInt(TAG_GENERATION));
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
                continue;
            }
            if (cluster.firstExtractionMillis() != 0L) {
                manager.runningTimers.add(key);
            }
        }
        ListTag stale = tag.getList(TAG_STALE, Tag.TAG_COMPOUND);
        for (int index = 0; index < stale.size(); index++) {
            CompoundTag staleTag = stale.getCompound(index);
            ResourceCluster.load(staleTag).ifPresent(cluster -> manager.staleClusters
                    .computeIfAbsent(staleTag.getLong("chunk"), key -> new ArrayList<>())
                    .add(cluster));
        }
        for (long key : tag.getLongArray(TAG_REMOVED)) {
            manager.removedChunks.add(key);
        }
        ListTag drillBindings = tag.getList(TAG_DRILL_BINDINGS, Tag.TAG_COMPOUND);
        for (int index = 0; index < drillBindings.size(); index++) {
            CompoundTag binding = drillBindings.getCompound(index);
            long clusterChunk = binding.getLong("clusterChunk");
            if (manager.clusters.containsKey(clusterChunk)) {
                manager.boundDrill.putIfAbsent(clusterChunk, binding.getLong("drillPos"));
            } else {
                manager.setDirty();
            }
        }
        ListTag pending = tag.getList(TAG_PENDING_CHUNKS, Tag.TAG_COMPOUND);
        for (int index = 0; index < pending.size(); index++) {
            CompoundTag pendingTag = pending.getCompound(index);
            if (pendingTag.contains("chunk", Tag.TAG_LONG) && pendingTag.contains("due", Tag.TAG_LONG)) {
                manager.pendingChunks.merge(
                        pendingTag.getLong("chunk"),
                        Math.max(0L, pendingTag.getLong("due")),
                        Math::min
                );
            }
        }
        if (tag.getInt(TAG_VERSION) < DATA_VERSION) {
            manager.setDirty();
        }
        return manager;
    }

    public record ClusterView(BlockPos basePos, ResourceClusterType type, int richness) {
    }

    public record SurfaceClusterView(
            long chunk,
            BlockPos pos,
            ResourceClusterType type,
            int richness,
            Long boundDrill,
            ReserveView reserve
    ) {
    }

    public record ReserveView(
            int remaining,
            int limit,
            long restoreInMillis,
            boolean timerRunning,
            boolean exhausted
    ) {
    }

    public record MaintenancePreview(
            int clusters,
            int runningTimers,
            int exhausted,
            int tombstones,
            int drillBindings
    ) {
    }

    public record ChunkDiagnostic(
            ChunkPos chunk,
            ResourceZone zone,
            String oreVeinSize,
            ChunkPos surfaceCandidateChunk,
            BlockPos surfacePosition,
            ResourceClusterType surfaceType,
            String surfaceReason,
            int pendingChunks,
            int knownClusters
    ) {
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

    private static final class ResourceCluster {
        private static final String TAG_ID = "id";
        private static final String TAG_BASE_POS = "basePos";
        private static final String TAG_TYPE = "type";
        private static final String TAG_RICHNESS = "richness";
        private static final String TAG_ITEM_DISPLAY = "itemDisplay";
        private static final String TAG_TEXT_DISPLAY = "textDisplay";
        private static final String TAG_SPENT = "spent";
        private static final String TAG_FIRST_EXTRACTION = "firstExtraction";
        private static final String TAG_EXHAUSTED = "exhausted";

        private final UUID id;
        private final BlockPos basePos;
        private final ResourceClusterType type;
        private final int richness;
        private UUID itemDisplayId;
        private UUID textDisplayId;
        private int spent;
        private long firstExtractionMillis;
        private boolean exhausted;

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

        private int spent() {
            return spent;
        }

        private void setSpent(int spent) {
            this.spent = Math.max(0, spent);
        }

        private long firstExtractionMillis() {
            return firstExtractionMillis;
        }

        private void setFirstExtractionMillis(long firstExtractionMillis) {
            this.firstExtractionMillis = Math.max(0L, firstExtractionMillis);
        }

        private boolean exhausted() {
            return exhausted;
        }

        private void setExhausted(boolean exhausted) {
            this.exhausted = exhausted;
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, id);
            tag.put(TAG_BASE_POS, NbtUtils.writeBlockPos(basePos));
            tag.putString(TAG_TYPE, type.id());
            tag.putInt(TAG_RICHNESS, richness);
            tag.putUUID(TAG_ITEM_DISPLAY, itemDisplayId);
            tag.putUUID(TAG_TEXT_DISPLAY, textDisplayId);
            tag.putInt(TAG_SPENT, spent);
            tag.putLong(TAG_FIRST_EXTRACTION, firstExtractionMillis);
            tag.putBoolean(TAG_EXHAUSTED, exhausted);
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
            ResourceCluster cluster = new ResourceCluster(
                    tag.getUUID(TAG_ID),
                    basePos.get(),
                    type.get(),
                    richness,
                    tag.getUUID(TAG_ITEM_DISPLAY),
                    tag.getUUID(TAG_TEXT_DISPLAY)
            );
            cluster.setSpent(tag.getInt(TAG_SPENT));
            cluster.setFirstExtractionMillis(tag.getLong(TAG_FIRST_EXTRACTION));
            cluster.setExhausted(tag.getBoolean(TAG_EXHAUSTED));
            return Optional.of(cluster);
        }
    }
}
