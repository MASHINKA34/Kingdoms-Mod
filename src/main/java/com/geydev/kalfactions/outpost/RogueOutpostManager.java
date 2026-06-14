package com.geydev.kalfactions.outpost;

import com.geydev.kalfactions.claim.ClaimKey;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public final class RogueOutpostManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_rogue_outposts";
    public static final String GARRISON_TAG = "kingdoms_rogue_garrison";
    public static final String OUTPOST_ID_DATA = "kingdoms_rogue_outpost";
    public static final Factory<RogueOutpostManager> FACTORY =
        new Factory<>(RogueOutpostManager::new, RogueOutpostManager::load);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_OUTPOSTS = "outposts";

    private final Map<UUID, RogueOutpost> outposts = new LinkedHashMap<>();
    private final Map<ClaimKey, UUID> chunkIndex = new HashMap<>();
    private final Map<UUID, UUID> garrisonIndex = new HashMap<>();

    public static RogueOutpostManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static RogueOutpostManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    public synchronized boolean isRogueChunk(ClaimKey key) {
        return chunkIndex.containsKey(key);
    }

    public synchronized List<RogueOutpost> all() {
        return List.copyOf(outposts.values());
    }

    public synchronized Optional<RogueOutpost> byCore(ServerLevel level, BlockPos corePos) {
        return outposts.values().stream()
            .filter(outpost -> outpost.dimension().equals(level.dimension().location().toString())
                && outpost.corePos().equals(corePos))
            .findFirst();
    }

    public synchronized void add(RogueOutpost outpost) {
        outposts.put(outpost.id(), outpost);
        for (ClaimKey key : outpost.chunks()) {
            chunkIndex.put(key, outpost.id());
        }
        for (UUID garrisonId : outpost.garrison()) {
            garrisonIndex.put(garrisonId, outpost.id());
        }
        setDirty();
    }

    public synchronized Optional<RogueOutpost> remove(UUID outpostId) {
        RogueOutpost removed = outposts.remove(outpostId);
        if (removed == null) {
            return Optional.empty();
        }
        chunkIndex.values().removeIf(id -> id.equals(outpostId));
        garrisonIndex.values().removeIf(id -> id.equals(outpostId));
        setDirty();
        return Optional.of(removed);
    }

    public synchronized Optional<UUID> onGarrisonKilled(UUID entityId) {
        UUID outpostId = garrisonIndex.remove(entityId);
        if (outpostId == null) {
            return Optional.empty();
        }
        RogueOutpost outpost = outposts.get(outpostId);
        if (outpost != null) {
            outpost.decrementGarrison();
            setDirty();
        }
        return Optional.of(outpostId);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag outpostsTag = new ListTag();
        for (RogueOutpost outpost : outposts.values()) {
            outpostsTag.add(outpost.save());
        }
        tag.put(TAG_OUTPOSTS, outpostsTag);
        return tag;
    }

    private static RogueOutpostManager load(CompoundTag tag, HolderLookup.Provider registries) {
        RogueOutpostManager manager = new RogueOutpostManager();
        ListTag outpostsTag = tag.getList(TAG_OUTPOSTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < outpostsTag.size(); index++) {
            Optional<RogueOutpost> loaded = RogueOutpost.load(outpostsTag.getCompound(index));
            if (loaded.isEmpty()) {
                LOGGER.warn("Skipped invalid rogue outpost entry at NBT index {}", index);
                manager.setDirty();
                continue;
            }
            RogueOutpost outpost = loaded.get();
            manager.outposts.put(outpost.id(), outpost);
            for (ClaimKey key : outpost.chunks()) {
                manager.chunkIndex.put(key, outpost.id());
            }
            for (UUID garrisonId : outpost.garrison()) {
                manager.garrisonIndex.put(garrisonId, outpost.id());
            }
        }
        return manager;
    }

    public static final class RogueOutpost {
        private static final String TAG_ID = "id";
        private static final String TAG_DIMENSION = "dimension";
        private static final String TAG_CORE = "core";
        private static final String TAG_CHUNKS = "chunks";
        private static final String TAG_GARRISON = "garrison";
        private static final String TAG_GARRISON_REMAINING = "garrisonRemaining";
        private static final String TAG_PREVIOUS_OWNER = "previousOwner";
        private static final String TAG_PREVIOUS_OWNER_NAME = "previousOwnerName";

        private final UUID id;
        private final String dimension;
        private final BlockPos corePos;
        private final Set<ClaimKey> chunks;
        private final List<UUID> garrison;
        private final UUID previousOwnerId;
        private final String previousOwnerName;
        private int garrisonRemaining;

        public RogueOutpost(
            UUID id,
            String dimension,
            BlockPos corePos,
            Set<ClaimKey> chunks,
            List<UUID> garrison,
            int garrisonRemaining,
            UUID previousOwnerId,
            String previousOwnerName
        ) {
            this.id = Objects.requireNonNull(id, "id");
            this.dimension = Objects.requireNonNull(dimension, "dimension");
            this.corePos = corePos.immutable();
            this.chunks = Set.copyOf(chunks);
            this.garrison = List.copyOf(garrison);
            this.garrisonRemaining = Math.max(0, garrisonRemaining);
            this.previousOwnerId = previousOwnerId;
            this.previousOwnerName = previousOwnerName == null ? "" : previousOwnerName;
        }

        public UUID id() {
            return id;
        }

        public String dimension() {
            return dimension;
        }

        public BlockPos corePos() {
            return corePos;
        }

        public Set<ClaimKey> chunks() {
            return chunks;
        }

        public List<UUID> garrison() {
            return garrison;
        }

        public int garrisonRemaining() {
            return garrisonRemaining;
        }

        public UUID previousOwnerId() {
            return previousOwnerId;
        }

        public String previousOwnerName() {
            return previousOwnerName;
        }

        void decrementGarrison() {
            garrisonRemaining = Math.max(0, garrisonRemaining - 1);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, id);
            tag.putString(TAG_DIMENSION, dimension);
            tag.put(TAG_CORE, NbtUtils.writeBlockPos(corePos));
            ListTag chunksTag = new ListTag();
            for (ClaimKey key : chunks) {
                chunksTag.add(key.save());
            }
            tag.put(TAG_CHUNKS, chunksTag);
            ListTag garrisonTag = new ListTag();
            for (UUID garrisonId : garrison) {
                garrisonTag.add(NbtUtils.createUUID(garrisonId));
            }
            tag.put(TAG_GARRISON, garrisonTag);
            tag.putInt(TAG_GARRISON_REMAINING, garrisonRemaining);
            if (previousOwnerId != null) {
                tag.putUUID(TAG_PREVIOUS_OWNER, previousOwnerId);
            }
            tag.putString(TAG_PREVIOUS_OWNER_NAME, previousOwnerName);
            return tag;
        }

        static Optional<RogueOutpost> load(CompoundTag tag) {
            if (!tag.hasUUID(TAG_ID)) {
                return Optional.empty();
            }
            Optional<BlockPos> core = NbtUtils.readBlockPos(tag, TAG_CORE);
            if (core.isEmpty()) {
                return Optional.empty();
            }
            Set<ClaimKey> chunks = new LinkedHashSet<>();
            ListTag chunksTag = tag.getList(TAG_CHUNKS, Tag.TAG_COMPOUND);
            for (int index = 0; index < chunksTag.size(); index++) {
                Optional<ClaimKey> key = ClaimKey.load(chunksTag.getCompound(index));
                if (key.isEmpty()) {
                    return Optional.empty();
                }
                chunks.add(key.get());
            }
            List<UUID> garrison = new ArrayList<>();
            ListTag garrisonTag = tag.getList(TAG_GARRISON, Tag.TAG_INT_ARRAY);
            for (int index = 0; index < garrisonTag.size(); index++) {
                try {
                    garrison.add(NbtUtils.loadUUID(garrisonTag.get(index)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return Optional.of(new RogueOutpost(
                tag.getUUID(TAG_ID),
                tag.getString(TAG_DIMENSION),
                core.get(),
                chunks,
                garrison,
                tag.getInt(TAG_GARRISON_REMAINING),
                tag.hasUUID(TAG_PREVIOUS_OWNER) ? tag.getUUID(TAG_PREVIOUS_OWNER) : null,
                tag.getString(TAG_PREVIOUS_OWNER_NAME)
            ));
        }
    }

    private RogueOutpostManager() {
    }
}
