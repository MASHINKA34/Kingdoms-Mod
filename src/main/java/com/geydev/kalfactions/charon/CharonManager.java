package com.geydev.kalfactions.charon;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class CharonManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_charon";
    public static final Factory<CharonManager> FACTORY = new Factory<>(CharonManager::new, CharonManager::load);

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_ID = "id";
    private static final String TAG_DEATH = "death";
    private static final String TAG_COOLDOWN_UNTIL = "cooldownUntil";
    private static final String TAG_GHOST = "ghost";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";
    private static final String TAG_AT = "at";
    private static final String TAG_ENDS_AT = "endsAt";
    private static final String TAG_HEALTH = "health";

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static CharonManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Optional<Entry> entry(UUID playerId) {
        return Optional.ofNullable(entries.get(playerId));
    }

    public synchronized Optional<Ghost> ghost(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.ghost());
    }

    public synchronized boolean hasGhosts() {
        for (Entry entry : entries.values()) {
            if (entry.ghost() != null) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<UUID> ghostPlayers() {
        List<UUID> result = new ArrayList<>();
        entries.forEach((playerId, entry) -> {
            if (entry.ghost() != null) {
                result.add(playerId);
            }
        });
        return result;
    }

    public synchronized void recordDeath(UUID playerId, GlobalPos pos, long atMillis) {
        update(playerId, entry -> entry.withDeath(new Death(pos, atMillis)));
    }

    public synchronized void setCooldownUntil(UUID playerId, long untilMillis) {
        update(playerId, entry -> entry.withCooldown(Math.max(0L, untilMillis)));
    }

    public synchronized void startGhost(UUID playerId, Ghost ghost) {
        update(playerId, entry -> entry.withGhost(Objects.requireNonNull(ghost, "ghost")));
    }

    public synchronized boolean clearGhost(UUID playerId) {
        Entry entry = entries.get(playerId);
        if (entry == null || entry.ghost() == null) {
            return false;
        }
        update(playerId, current -> current.withGhost(null));
        return true;
    }

    public synchronized boolean forget(UUID playerId) {
        if (entries.remove(playerId) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    private void update(UUID playerId, UnaryOperator<Entry> change) {
        Entry updated = change.apply(entries.getOrDefault(playerId, Entry.EMPTY));
        if (updated.isEmpty()) {
            entries.remove(playerId);
        } else {
            entries.put(playerId, updated);
        }
        setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag list = new ListTag();
        entries.forEach((playerId, entry) -> {
            CompoundTag saved = new CompoundTag();
            saved.putUUID(TAG_ID, playerId);
            if (entry.death() != null) {
                CompoundTag death = writeGlobalPos(entry.death().pos());
                death.putLong(TAG_AT, entry.death().atMillis());
                saved.put(TAG_DEATH, death);
            }
            saved.putLong(TAG_COOLDOWN_UNTIL, entry.cooldownUntilMillis());
            if (entry.ghost() != null) {
                CompoundTag ghost = writeGlobalPos(entry.ghost().returnPos());
                ghost.putLong(TAG_ENDS_AT, entry.ghost().endsAtGameTime());
                ghost.putFloat(TAG_HEALTH, entry.ghost().savedHealth());
                saved.put(TAG_GHOST, ghost);
            }
            list.add(saved);
        });
        tag.put(TAG_PLAYERS, list);
        return tag;
    }

    private static CharonManager load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        CharonManager manager = new CharonManager();
        ListTag list = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            if (!entryTag.hasUUID(TAG_ID)) {
                continue;
            }
            Death death = null;
            if (entryTag.contains(TAG_DEATH, Tag.TAG_COMPOUND)) {
                CompoundTag deathTag = entryTag.getCompound(TAG_DEATH);
                GlobalPos pos = readGlobalPos(deathTag).orElse(null);
                if (pos != null) {
                    death = new Death(pos, Math.max(0L, deathTag.getLong(TAG_AT)));
                }
            }
            Ghost ghost = null;
            if (entryTag.contains(TAG_GHOST, Tag.TAG_COMPOUND)) {
                CompoundTag ghostTag = entryTag.getCompound(TAG_GHOST);
                GlobalPos pos = readGlobalPos(ghostTag).orElse(null);
                if (pos != null) {
                    ghost = new Ghost(pos, ghostTag.getLong(TAG_ENDS_AT), ghostTag.getFloat(TAG_HEALTH));
                }
            }
            Entry entry = new Entry(death, Math.max(0L, entryTag.getLong(TAG_COOLDOWN_UNTIL)), ghost);
            if (!entry.isEmpty()) {
                manager.entries.put(entryTag.getUUID(TAG_ID), entry);
            }
        }
        if (FORMAT.outdated(saved)) {
            manager.setDirty();
        }
        return manager;
    }

    private static CompoundTag writeGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, pos.dimension().location().toString());
        tag.putLong(TAG_POS, pos.pos().asLong());
        return tag;
    }

    private static Optional<GlobalPos> readGlobalPos(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimension == null || !tag.contains(TAG_POS, Tag.TAG_LONG)) {
            return Optional.empty();
        }
        return Optional.of(GlobalPos.of(
                ResourceKey.create(Registries.DIMENSION, dimension),
                BlockPos.of(tag.getLong(TAG_POS))
        ));
    }

    public record Death(GlobalPos pos, long atMillis) {
        public Death {
            Objects.requireNonNull(pos, "pos");
        }
    }

    public record Ghost(GlobalPos returnPos, long endsAtGameTime, float savedHealth) {
        public Ghost {
            Objects.requireNonNull(returnPos, "returnPos");
        }
    }

    public record Entry(@Nullable Death death, long cooldownUntilMillis, @Nullable Ghost ghost) {
        static final Entry EMPTY = new Entry(null, 0L, null);

        Entry withDeath(Death value) {
            return new Entry(value, cooldownUntilMillis, ghost);
        }

        Entry withCooldown(long value) {
            return new Entry(death, value, ghost);
        }

        Entry withGhost(@Nullable Ghost value) {
            return new Entry(death, cooldownUntilMillis, value);
        }

        boolean isEmpty() {
            return death == null && cooldownUntilMillis <= 0L && ghost == null;
        }
    }
}
