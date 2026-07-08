package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
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
    private static final int CELL_SIZE_CHUNKS = 16;
    private static final int LOAD_DELAY_TICKS = 20;
    private static final int MAX_PLACEMENTS_PER_TICK = 16;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long CELL_X_SALT = 0x632BE59BD9B4E019L;
    private static final long CELL_Z_SALT = 0x8CB92BA72F3D8DD7L;
    private static final String TAG_CLUSTERS = "clusters";
    private static final String TAG_REMOVED = "removed";
    private static final String ENTITY_CLUSTER_KEY = KalFactions.MOD_ID + "ResourceCluster";
    private static final String ENTITY_ROLE_KEY = KalFactions.MOD_ID + "ResourceClusterRole";
    private static final String ITEM_ROLE = "item";
    private static final String TEXT_ROLE = "text";

    private final Map<Long, ResourceCluster> clusters = new LinkedHashMap<>();
    private final Map<Long, Long> pendingChunks = new HashMap<>();
    private final Set<Long> activeChunks = new HashSet<>();
    private final Map<Long, Long> boundDrill = new HashMap<>();
    private final Set<Long> removedChunks = new HashSet<>();

    public static ResourceClusterManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Optional<ClusterView> clusterAt(ChunkPos chunkPos) {
        ResourceCluster cluster = clusters.get(chunkPos.toLong());
        return cluster == null
                ? Optional.empty()
                : Optional.of(new ClusterView(cluster.type(), cluster.richness()));
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
        processPending(level, gameTime);
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
            }
        }
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
        ListTag list = new ListTag();
        for (ResourceCluster cluster : clusters.values()) {
            list.add(cluster.save());
        }
        tag.put(TAG_CLUSTERS, list);
        tag.putLongArray(TAG_REMOVED, removedChunks.stream().mapToLong(Long::longValue).toArray());
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
        return manager;
    }

    public record ClusterView(ResourceClusterType type, int richness) {
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
