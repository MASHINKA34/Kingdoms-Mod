package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.geydev.kalfactions.territory.WorldZonePolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class DungeonManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_dungeons";
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_DUNGEONS = 512;
    public static final int MAX_CHUNKS_PER_DUNGEON = 4096;
    public static final Factory<DungeonManager> FACTORY =
            new Factory<>(DungeonManager::new, DungeonManager::load);

    private static final String TAG_DUNGEONS = "dungeons";
    private static final String TAG_NEXT_ID = "nextId";
    private static final String TAG_ID = "id";
    private static final String TAG_NAME = "name";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_CORE = "core";
    private static final String TAG_CREATED_AT = "createdAt";
    private static final String TAG_CONTAINERS = "containers";
    private static final String TAG_POS = "pos";
    private static final String TAG_LOOT_TABLE = "lootTable";
    private static final String TAG_LAST_FILLED = "lastFilled";

    private final Map<Integer, Dungeon> dungeons = new LinkedHashMap<>();
    private final Map<ClaimKey, Integer> chunkIndex = new LinkedHashMap<>();
    private int nextId = 1;
    private long revision;

    public static DungeonManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static DungeonManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public static UUID mapId(int dungeonId) {
        return new UUID(0x4455_4E47_454F_4E5FL, dungeonId);
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<DungeonView> all() {
        return dungeons.values().stream().map(Dungeon::view).toList();
    }

    public synchronized int count() {
        return dungeons.size();
    }

    public synchronized Optional<DungeonView> byId(int id) {
        Dungeon dungeon = dungeons.get(id);
        return dungeon == null ? Optional.empty() : Optional.of(dungeon.view());
    }

    public synchronized Optional<DungeonView> byName(String name) {
        String normalized = normalizeName(name).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return dungeons.values().stream()
                .filter(dungeon -> dungeon.name.toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .map(Dungeon::view);
    }

    public synchronized Optional<DungeonView> dungeonAt(ClaimKey key) {
        Integer id = chunkIndex.get(key);
        Dungeon dungeon = id == null ? null : dungeons.get(id);
        return dungeon == null ? Optional.empty() : Optional.of(dungeon.view());
    }

    public synchronized Optional<DungeonView> byCore(ResourceKey<Level> dimension, BlockPos corePos) {
        return dungeons.values().stream()
                .filter(dungeon -> dungeon.dimension.equals(dimension) && dungeon.corePos.equals(corePos))
                .findFirst()
                .map(Dungeon::view);
    }

    public synchronized boolean isDungeon(ClaimKey key) {
        return chunkIndex.containsKey(key);
    }

    public synchronized boolean isDungeon(Level level, BlockPos pos) {
        return isDungeon(ClaimKey.of(level, pos));
    }

    public synchronized boolean isDungeon(Level level, ChunkPos chunk) {
        return isDungeon(ClaimKey.of(level, chunk));
    }

    public synchronized boolean isEmpty() {
        return chunkIndex.isEmpty();
    }

    public synchronized String suggestName() {
        for (int index = 1; index <= MAX_DUNGEONS + 1; index++) {
            String candidate = "Данж " + index;
            if (byName(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "Данж " + nextId;
    }

    public synchronized CreateResult create(ServerLevel level, BlockPos corePos, String requestedName) {
        if (dungeons.size() >= MAX_DUNGEONS) {
            return new CreateResult(Reason.TOO_MANY, null);
        }
        if (!WorldZonePolicy.isBlack(level, corePos)) {
            return new CreateResult(Reason.NOT_BLACK, null);
        }
        String name = normalizeName(requestedName);
        if (name.isEmpty()) {
            name = suggestName();
        }
        if (byName(name).isPresent()) {
            return new CreateResult(Reason.NAME_TAKEN, null);
        }
        int id = nextId++;
        Dungeon dungeon = new Dungeon(
                id,
                name,
                level.dimension(),
                corePos.immutable(),
                DungeonClock.now()
        );
        dungeons.put(id, dungeon);
        revision++;
        setDirty();
        return new CreateResult(Reason.OK, dungeon.view());
    }

    public synchronized boolean bindCore(int id, BlockPos corePos) {
        Dungeon dungeon = dungeons.get(id);
        if (dungeon == null || dungeon.corePos.equals(corePos)) {
            return false;
        }
        dungeon.corePos = corePos.immutable();
        setDirty();
        return true;
    }

    public synchronized boolean remove(int id) {
        Dungeon dungeon = dungeons.remove(id);
        if (dungeon == null) {
            return false;
        }
        chunkIndex.entrySet().removeIf(entry -> entry.getValue() == id);
        revision++;
        setDirty();
        return true;
    }

    public synchronized Reason rename(int id, String requestedName) {
        Dungeon dungeon = dungeons.get(id);
        if (dungeon == null) {
            return Reason.NOT_FOUND;
        }
        String name = normalizeName(requestedName);
        if (name.isEmpty()) {
            return Reason.NAME_EMPTY;
        }
        DungeonView existing = byName(name).orElse(null);
        if (existing != null && existing.id() != id) {
            return Reason.NAME_TAKEN;
        }
        if (dungeon.name.equals(name)) {
            return Reason.OK;
        }
        dungeon.name = name;
        revision++;
        setDirty();
        return Reason.OK;
    }

    public synchronized Reason canMark(ServerLevel level, int dungeonId, ClaimKey key) {
        if (!dungeons.containsKey(dungeonId)) {
            return Reason.NOT_FOUND;
        }
        Integer owner = chunkIndex.get(key);
        if (owner != null) {
            return owner == dungeonId ? Reason.OK : Reason.OTHER_DUNGEON;
        }
        if (!key.dimension().equals(level.dimension()) || !WorldZonePolicy.isBlack(level, key.chunk())) {
            return Reason.NOT_BLACK;
        }
        if (SanctuaryManager.get(level).isSanctuary(key)) {
            return Reason.SANCTUARY;
        }
        if (FactionManager.get(level).getFactionAt(key).isPresent()
                || RogueOutpostManager.get(level).isRogueChunk(key)) {
            return Reason.CLAIMED;
        }
        if (QuarryManager.get(level).isQuarry(level, key.chunk().getWorldPosition())) {
            return Reason.CLAIMED;
        }
        return Reason.OK;
    }

    public synchronized MarkResult setClaims(
            ServerLevel level,
            int dungeonId,
            Collection<ClaimKey> keys,
            boolean marked
    ) {
        Dungeon dungeon = dungeons.get(dungeonId);
        if (dungeon == null) {
            return new MarkResult(0, 0, Reason.NOT_FOUND);
        }
        int changed = 0;
        int blocked = 0;
        Reason firstBlock = Reason.OK;
        for (ClaimKey key : keys) {
            if (marked) {
                if (dungeon.chunks.contains(key)) {
                    continue;
                }
                if (dungeon.chunks.size() >= MAX_CHUNKS_PER_DUNGEON) {
                    blocked++;
                    if (firstBlock == Reason.OK) {
                        firstBlock = Reason.TOO_MANY_CHUNKS;
                    }
                    continue;
                }
                Reason reason = canMark(level, dungeonId, key);
                if (reason != Reason.OK) {
                    blocked++;
                    if (firstBlock == Reason.OK) {
                        firstBlock = reason;
                    }
                    continue;
                }
                dungeon.chunks.add(key);
                chunkIndex.put(key, dungeonId);
                changed++;
            } else if (dungeon.chunks.remove(key)) {
                chunkIndex.remove(key);
                dungeon.containers.keySet().removeIf(packed -> key.contains(BlockPos.of(packed)));
                changed++;
            }
        }
        if (changed > 0) {
            revision++;
            setDirty();
        }
        return new MarkResult(changed, blocked, firstBlock);
    }

    public synchronized Optional<LootEntry> lootAt(Level level, BlockPos pos) {
        Dungeon dungeon = dungeonFor(level, pos);
        LootEntry entry = dungeon == null ? null : dungeon.containers.get(pos.asLong());
        return Optional.ofNullable(entry);
    }

    public synchronized boolean markLoot(Level level, BlockPos pos, ResourceLocation lootTable, long filledAt) {
        Dungeon dungeon = dungeonFor(level, pos);
        if (dungeon == null) {
            return false;
        }
        dungeon.containers.put(pos.asLong(), new LootEntry(lootTable, filledAt));
        setDirty();
        return true;
    }

    public synchronized boolean unmarkLoot(Level level, BlockPos pos) {
        Dungeon dungeon = dungeonFor(level, pos);
        if (dungeon == null || dungeon.containers.remove(pos.asLong()) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized boolean touchLoot(Level level, BlockPos pos, long filledAt) {
        Dungeon dungeon = dungeonFor(level, pos);
        LootEntry entry = dungeon == null ? null : dungeon.containers.get(pos.asLong());
        if (entry == null) {
            return false;
        }
        dungeon.containers.put(pos.asLong(), new LootEntry(entry.lootTable(), filledAt));
        setDirty();
        return true;
    }

    public synchronized int resetLoot(int dungeonId) {
        Dungeon dungeon = dungeons.get(dungeonId);
        if (dungeon == null || dungeon.containers.isEmpty()) {
            return 0;
        }
        dungeon.containers.replaceAll((packed, entry) -> new LootEntry(entry.lootTable(), 0L));
        setDirty();
        return dungeon.containers.size();
    }

    public synchronized int lootCount(int dungeonId) {
        Dungeon dungeon = dungeons.get(dungeonId);
        return dungeon == null ? 0 : dungeon.containers.size();
    }

    private Dungeon dungeonFor(Level level, BlockPos pos) {
        Integer id = chunkIndex.get(ClaimKey.of(level, pos));
        return id == null ? null : dungeons.get(id);
    }

    public static String normalizeName(String value) {
        StringBuilder normalized = new StringBuilder(MAX_NAME_LENGTH);
        if (value != null) {
            value.codePoints()
                    .filter(codePoint -> !Character.isISOControl(codePoint))
                    .limit(MAX_NAME_LENGTH)
                    .forEach(normalized::appendCodePoint);
        }
        return normalized.toString().trim().replaceAll("\\s+", " ");
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Dungeon dungeon : dungeons.values()) {
            list.add(dungeon.save());
        }
        tag.put(TAG_DUNGEONS, list);
        tag.putInt(TAG_NEXT_ID, nextId);
        return tag;
    }

    static DungeonManager load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonManager manager = new DungeonManager();
        ListTag list = tag.getList(TAG_DUNGEONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            Dungeon dungeon = Dungeon.load(list.getCompound(index));
            if (dungeon == null || manager.dungeons.containsKey(dungeon.id)) {
                continue;
            }
            manager.dungeons.put(dungeon.id, dungeon);
            for (ClaimKey key : dungeon.chunks) {
                manager.chunkIndex.putIfAbsent(key, dungeon.id);
            }
            manager.nextId = Math.max(manager.nextId, dungeon.id + 1);
        }
        manager.nextId = Math.max(manager.nextId, tag.getInt(TAG_NEXT_ID));
        manager.revision = 1L;
        return manager;
    }

    public enum Reason {
        OK,
        NOT_FOUND,
        NOT_BLACK,
        SANCTUARY,
        CLAIMED,
        OTHER_DUNGEON,
        NAME_TAKEN,
        NAME_EMPTY,
        TOO_MANY,
        TOO_MANY_CHUNKS
    }

    public record CreateResult(Reason reason, DungeonView dungeon) {
        public boolean successful() {
            return reason == Reason.OK && dungeon != null;
        }
    }

    public record MarkResult(int changed, int blocked, Reason reason) {
    }

    public record LootEntry(ResourceLocation lootTable, long lastFilled) {
    }

    public record DungeonView(
            int id,
            String name,
            ResourceKey<Level> dimension,
            BlockPos corePos,
            Set<ClaimKey> chunks,
            long createdAt,
            int containerCount
    ) {
    }

    private static final class Dungeon {
        private final int id;
        private final ResourceKey<Level> dimension;
        private final long createdAt;
        private final Set<ClaimKey> chunks = new LinkedHashSet<>();
        private final Map<Long, LootEntry> containers = new LinkedHashMap<>();
        private BlockPos corePos;
        private String name;

        private Dungeon(int id, String name, ResourceKey<Level> dimension, BlockPos corePos, long createdAt) {
            this.id = id;
            this.name = name;
            this.dimension = dimension;
            this.corePos = corePos;
            this.createdAt = createdAt;
        }

        private DungeonView view() {
            return new DungeonView(
                    id,
                    name,
                    dimension,
                    corePos,
                    Set.copyOf(chunks),
                    createdAt,
                    containers.size()
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_ID, id);
            tag.putString(TAG_NAME, name);
            tag.putString(TAG_DIMENSION, dimension.location().toString());
            tag.putLong(TAG_CORE, corePos.asLong());
            tag.putLong(TAG_CREATED_AT, createdAt);
            ListTag chunkList = new ListTag();
            chunks.stream().sorted().map(ClaimKey::save).forEach(chunkList::add);
            tag.put(TAG_CHUNKS, chunkList);
            ListTag containerList = new ListTag();
            for (Map.Entry<Long, LootEntry> entry : containers.entrySet()) {
                CompoundTag containerTag = new CompoundTag();
                containerTag.putLong(TAG_POS, entry.getKey());
                containerTag.putString(TAG_LOOT_TABLE, entry.getValue().lootTable().toString());
                containerTag.putLong(TAG_LAST_FILLED, entry.getValue().lastFilled());
                containerList.add(containerTag);
            }
            tag.put(TAG_CONTAINERS, containerList);
            return tag;
        }

        private static Dungeon load(CompoundTag tag) {
            ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
            if (dimensionId == null || !tag.contains(TAG_ID, Tag.TAG_INT)) {
                return null;
            }
            Dungeon dungeon = new Dungeon(
                    tag.getInt(TAG_ID),
                    normalizeName(tag.getString(TAG_NAME)),
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(tag.getLong(TAG_CORE)),
                    tag.getLong(TAG_CREATED_AT)
            );
            if (dungeon.name.isEmpty()) {
                dungeon.name = "Данж " + dungeon.id;
            }
            ListTag chunkList = tag.getList(TAG_CHUNKS, Tag.TAG_COMPOUND);
            for (int index = 0; index < chunkList.size(); index++) {
                ClaimKey.load(chunkList.getCompound(index)).ifPresent(dungeon.chunks::add);
            }
            ListTag containerList = tag.getList(TAG_CONTAINERS, Tag.TAG_COMPOUND);
            for (int index = 0; index < containerList.size(); index++) {
                CompoundTag containerTag = containerList.getCompound(index);
                ResourceLocation lootTable = ResourceLocation.tryParse(containerTag.getString(TAG_LOOT_TABLE));
                if (lootTable == null) {
                    continue;
                }
                dungeon.containers.put(
                        containerTag.getLong(TAG_POS),
                        new LootEntry(lootTable, containerTag.getLong(TAG_LAST_FILLED))
                );
            }
            return dungeon;
        }
    }

    private List<ClaimKey> chunkList(ResourceKey<Level> dimension) {
        List<ClaimKey> keys = new ArrayList<>();
        for (ClaimKey key : chunkIndex.keySet()) {
            if (key.dimension().equals(dimension)) {
                keys.add(key);
            }
        }
        return keys;
    }

    public synchronized List<ClaimKey> claimsIn(ResourceKey<Level> dimension) {
        return List.copyOf(chunkList(dimension));
    }
}
