package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class FaithManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_faith";
    public static final Factory<FaithManager> FACTORY = new Factory<>(FaithManager::new, FaithManager::load);

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_FACTIONS = "factions";
    private static final String TAG_FACTION_ID = "id";
    private static final String TAG_GODS = "gods";
    private static final String TAG_GOD = "god";
    private static final String TAG_LEVEL = "level";
    private static final String TAG_NONCE = "nonce";
    private static final String TAG_DELIVERED = "delivered";
    private static final String TAG_SPURS = "spurs";
    private static final String TAG_KILLS = "kills";
    private static final String TAG_BUFF_END = "buffEnd";
    private static final String TAG_FORFEITS = "forfeits";
    private static final String TAG_PLAYER_ID = "player";
    private static final String TAG_WINDOWS = "windows";

    private static final class GodProgress {
        private int level = FaithGod.MIN_LEVEL;
        private int nonce;
        private int[] delivered = new int[0];
        private long spurs;
        private int kills;
        private long buffEndMillis;

        private void resetProgress() {
            delivered = new int[0];
            spurs = 0L;
            kills = 0;
        }
    }

    private final Map<UUID, EnumMap<FaithGod, GodProgress>> factions = new LinkedHashMap<>();
    private final Map<UUID, EnumMap<FaithGod, Long>> forfeitedWindows = new LinkedHashMap<>();

    public static FaithManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static FaithManager get(ServerLevel level) {
        return get(Objects.requireNonNull(level, "level").getServer());
    }

    private GodProgress progress(UUID factionId, FaithGod god) {
        return factions
                .computeIfAbsent(factionId, ignored -> new EnumMap<>(FaithGod.class))
                .computeIfAbsent(god, ignored -> new GodProgress());
    }

    public synchronized int level(UUID factionId, FaithGod god) {
        return progress(factionId, god).level;
    }

    public synchronized int nonce(UUID factionId, FaithGod god) {
        return progress(factionId, god).nonce;
    }

    public synchronized int[] delivered(UUID factionId, FaithGod god) {
        return progress(factionId, god).delivered.clone();
    }

    public synchronized int deliveredAt(UUID factionId, FaithGod god, int index) {
        return FaithQuest.deliveredAt(progress(factionId, god).delivered, index);
    }

    public synchronized long spursDelivered(UUID factionId, FaithGod god) {
        return progress(factionId, god).spurs;
    }

    public synchronized int kills(UUID factionId, FaithGod god) {
        return progress(factionId, god).kills;
    }

    public synchronized long buffEndMillis(UUID factionId, FaithGod god) {
        return progress(factionId, god).buffEndMillis;
    }

    public synchronized boolean buffActive(UUID factionId, FaithGod god, long nowMillis) {
        return progress(factionId, god).buffEndMillis > nowMillis;
    }

    public synchronized void addDelivered(UUID factionId, FaithGod god, int index, int amount) {
        if (index < 0 || amount <= 0) {
            return;
        }
        GodProgress state = progress(factionId, god);
        if (index >= state.delivered.length) {
            state.delivered = Arrays.copyOf(state.delivered, index + 1);
        }
        state.delivered[index] = Math.max(0, state.delivered[index] + amount);
        setDirty();
    }

    public synchronized void addSpurs(UUID factionId, FaithGod god, long amount) {
        if (amount <= 0L) {
            return;
        }
        GodProgress state = progress(factionId, god);
        state.spurs = Math.max(0L, state.spurs + amount);
        setDirty();
    }

    public synchronized void addKills(UUID factionId, FaithGod god, int amount) {
        if (amount <= 0) {
            return;
        }
        GodProgress state = progress(factionId, god);
        state.kills = Math.max(0, state.kills + amount);
        setDirty();
    }

    public synchronized boolean advanceLevel(UUID factionId, FaithGod god) {
        GodProgress state = progress(factionId, god);
        if (state.level >= FaithGod.MAX_LEVEL) {
            return false;
        }
        state.level++;
        state.resetProgress();
        setDirty();
        return true;
    }

    public synchronized void setLevel(UUID factionId, FaithGod god, int level) {
        GodProgress state = progress(factionId, god);
        state.level = Math.clamp(level, FaithGod.MIN_LEVEL, FaithGod.MAX_LEVEL);
        state.resetProgress();
        setDirty();
    }

    public synchronized int reroll(UUID factionId, FaithGod god) {
        GodProgress state = progress(factionId, god);
        state.nonce++;
        state.resetProgress();
        setDirty();
        return state.nonce;
    }

    public synchronized void activateBuff(UUID factionId, FaithGod god, long endMillis) {
        GodProgress state = progress(factionId, god);
        state.buffEndMillis = Math.max(0L, endMillis);
        setDirty();
    }

    public synchronized boolean clearBuff(UUID factionId, FaithGod god) {
        GodProgress state = progress(factionId, god);
        if (state.buffEndMillis == 0L) {
            return false;
        }
        state.buffEndMillis = 0L;
        setDirty();
        return true;
    }

    public synchronized void forfeit(UUID playerId, FaithGod god, long windowEndMillis) {
        if (windowEndMillis <= 0L) {
            return;
        }
        forfeitedWindows
                .computeIfAbsent(playerId, ignored -> new EnumMap<>(FaithGod.class))
                .put(god, windowEndMillis);
        setDirty();
    }

    public synchronized boolean hasForfeited(UUID playerId, FaithGod god, long windowEndMillis) {
        EnumMap<FaithGod, Long> windows = forfeitedWindows.get(playerId);
        if (windows == null) {
            return false;
        }
        Long forfeited = windows.get(god);
        return forfeited != null && forfeited == windowEndMillis;
    }

    public synchronized void pruneForfeits(long nowMillis) {
        boolean changed = false;
        var iterator = forfeitedWindows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, EnumMap<FaithGod, Long>> entry = iterator.next();
            changed |= entry.getValue().values().removeIf(window -> window <= nowMillis);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public synchronized void removeFaction(UUID factionId) {
        if (factions.remove(factionId) != null) {
            setDirty();
        }
    }

    public synchronized void pruneMissing(Collection<UUID> existingFactionIds) {
        if (factions.keySet().retainAll(Set.copyOf(existingFactionIds))) {
            setDirty();
        }
    }

    public synchronized Set<UUID> trackedFactions() {
        return Set.copyOf(factions.keySet());
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag factionsTag = new ListTag();
        factions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag factionTag = new CompoundTag();
                    factionTag.putUUID(TAG_FACTION_ID, entry.getKey());
                    ListTag godsTag = new ListTag();
                    for (FaithGod god : FaithGod.VALUES) {
                        GodProgress state = entry.getValue().get(god);
                        if (state == null) {
                            continue;
                        }
                        CompoundTag godTag = new CompoundTag();
                        godTag.putString(TAG_GOD, god.name());
                        godTag.putInt(TAG_LEVEL, state.level);
                        godTag.putInt(TAG_NONCE, state.nonce);
                        godTag.putIntArray(TAG_DELIVERED, state.delivered.clone());
                        godTag.putLong(TAG_SPURS, state.spurs);
                        godTag.putInt(TAG_KILLS, state.kills);
                        godTag.putLong(TAG_BUFF_END, state.buffEndMillis);
                        godsTag.add(godTag);
                    }
                    factionTag.put(TAG_GODS, godsTag);
                    factionsTag.add(factionTag);
                });
        tag.put(TAG_FACTIONS, factionsTag);

        ListTag forfeitsTag = new ListTag();
        forfeitedWindows.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag playerTag = new CompoundTag();
                    playerTag.putUUID(TAG_PLAYER_ID, entry.getKey());
                    ListTag windowsTag = new ListTag();
                    for (Map.Entry<FaithGod, Long> window : entry.getValue().entrySet()) {
                        CompoundTag windowTag = new CompoundTag();
                        windowTag.putString(TAG_GOD, window.getKey().name());
                        windowTag.putLong(TAG_BUFF_END, window.getValue());
                        windowsTag.add(windowTag);
                    }
                    playerTag.put(TAG_WINDOWS, windowsTag);
                    forfeitsTag.add(playerTag);
                });
        tag.put(TAG_FORFEITS, forfeitsTag);
        return tag;
    }

    private static FaithManager load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        boolean legacyFormat = FORMAT.outdated(saved);
        FaithManager manager = new FaithManager();
        ListTag factionsTag = tag.getList(TAG_FACTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < factionsTag.size(); index++) {
            CompoundTag factionTag = factionsTag.getCompound(index);
            if (!factionTag.hasUUID(TAG_FACTION_ID)) {
                continue;
            }
            EnumMap<FaithGod, GodProgress> gods = new EnumMap<>(FaithGod.class);
            ListTag godsTag = factionTag.getList(TAG_GODS, Tag.TAG_COMPOUND);
            for (int godIndex = 0; godIndex < godsTag.size(); godIndex++) {
                CompoundTag godTag = godsTag.getCompound(godIndex);
                FaithGod god = FaithGod.parse(godTag.getString(TAG_GOD)).orElse(null);
                if (god == null) {
                    continue;
                }
                GodProgress state = new GodProgress();
                state.level = Math.clamp(godTag.getInt(TAG_LEVEL), FaithGod.MIN_LEVEL, FaithGod.MAX_LEVEL);
                state.nonce = Math.max(0, godTag.getInt(TAG_NONCE));
                state.delivered = godTag.getIntArray(TAG_DELIVERED).clone();
                state.spurs = Math.max(0L, godTag.getLong(TAG_SPURS));
                state.kills = Math.max(0, godTag.getInt(TAG_KILLS));
                state.buffEndMillis = Math.max(0L, godTag.getLong(TAG_BUFF_END));
                gods.put(god, state);
            }
            manager.factions.put(factionTag.getUUID(TAG_FACTION_ID), gods);
        }
        ListTag forfeitsTag = tag.getList(TAG_FORFEITS, Tag.TAG_COMPOUND);
        for (int index = 0; index < forfeitsTag.size(); index++) {
            CompoundTag playerTag = forfeitsTag.getCompound(index);
            if (!playerTag.hasUUID(TAG_PLAYER_ID)) {
                continue;
            }
            EnumMap<FaithGod, Long> windows = new EnumMap<>(FaithGod.class);
            ListTag windowsTag = playerTag.getList(TAG_WINDOWS, Tag.TAG_COMPOUND);
            for (int windowIndex = 0; windowIndex < windowsTag.size(); windowIndex++) {
                CompoundTag windowTag = windowsTag.getCompound(windowIndex);
                FaithGod.parse(windowTag.getString(TAG_GOD))
                        .ifPresent(god -> windows.put(god, windowTag.getLong(TAG_BUFF_END)));
            }
            if (!windows.isEmpty()) {
                manager.forfeitedWindows.put(playerTag.getUUID(TAG_PLAYER_ID), windows);
            }
        }
        if (legacyFormat) {
            manager.setDirty();
        }
        return manager;
    }
}
