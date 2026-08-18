package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ScienceLedger extends SavedData {
    public static final String DATA_NAME = "kingdoms_science";
    public static final Factory<ScienceLedger> FACTORY = new Factory<>(ScienceLedger::new, ScienceLedger::load);
    public static final ZoneId DAY_ZONE = ZoneId.of("Europe/Moscow");

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_FACTIONS = "factions";
    private static final String TAG_FACTION_ID = "faction";
    private static final String TAG_DISCOVERIES = "discoveries";
    private static final String TAG_EPOCH_DAY = "epochDay";
    private static final String TAG_GRANTED = "granted";
    private static final String TAG_NOTIFIED = "notified";
    private static final String TAG_PLAYER = "player";

    private final Map<UUID, FactionScience> entries = new LinkedHashMap<>();

    public static ScienceLedger get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static ScienceLedger get(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return get(level.getServer());
    }

    public static long epochDay(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(DAY_ZONE).toLocalDate().toEpochDay();
    }

    public synchronized boolean isDiscovered(UUID factionId, ResourceLocation itemId) {
        FactionScience entry = entries.get(factionId);
        return entry != null && entry.discoveries.contains(itemId.toString());
    }

    public synchronized boolean discover(UUID factionId, ResourceLocation itemId) {
        if (factionId == null || itemId == null) {
            return false;
        }
        if (!entries.computeIfAbsent(factionId, ignored -> new FactionScience()).discoveries.add(itemId.toString())) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized int discoveryCount(UUID factionId) {
        FactionScience entry = entries.get(factionId);
        return entry == null ? 0 : entry.discoveries.size();
    }

    public synchronized long grantedToday(UUID factionId, long nowMillis) {
        FactionScience entry = entries.get(factionId);
        return entry == null || entry.epochDay != epochDay(nowMillis) ? 0L : entry.granted;
    }

    public synchronized long remainingToday(UUID factionId, long nowMillis, long dailyCap) {
        if (dailyCap <= 0L) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, dailyCap - grantedToday(factionId, nowMillis));
    }

    public synchronized void recordScience(UUID factionId, long nowMillis, long amount) {
        if (factionId == null || amount <= 0L) {
            return;
        }
        FactionScience entry = entries.computeIfAbsent(factionId, ignored -> new FactionScience());
        entry.rollOver(epochDay(nowMillis));
        entry.granted = saturatedAdd(entry.granted, amount);
        setDirty();
    }

    public synchronized boolean markCapNotified(UUID factionId, long nowMillis, UUID playerId) {
        if (factionId == null || playerId == null) {
            return false;
        }
        FactionScience entry = entries.computeIfAbsent(factionId, ignored -> new FactionScience());
        entry.rollOver(epochDay(nowMillis));
        if (!entry.notified.add(playerId)) {
            return false;
        }
        setDirty();
        return true;
    }

    public synchronized boolean resetDiscoveries(UUID factionId) {
        FactionScience entry = entries.get(factionId);
        if (entry == null || entry.discoveries.isEmpty()) {
            return false;
        }
        entry.discoveries.clear();
        setDirty();
        return true;
    }

    public synchronized boolean resetDaily(UUID factionId) {
        FactionScience entry = entries.get(factionId);
        if (entry == null || entry.granted == 0L && entry.notified.isEmpty()) {
            return false;
        }
        entry.granted = 0L;
        entry.notified.clear();
        setDirty();
        return true;
    }

    public synchronized void removeFaction(UUID factionId) {
        if (entries.remove(factionId) != null) {
            setDirty();
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag factionsTag = new ListTag();
        entries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag entryTag = entry.getValue().save();
                    entryTag.putUUID(TAG_FACTION_ID, entry.getKey());
                    factionsTag.add(entryTag);
                });
        tag.put(TAG_FACTIONS, factionsTag);
        return tag;
    }

    private static ScienceLedger load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        ScienceLedger ledger = new ScienceLedger();
        ListTag factionsTag = tag.getList(TAG_FACTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < factionsTag.size(); index++) {
            CompoundTag entryTag = factionsTag.getCompound(index);
            if (!entryTag.hasUUID(TAG_FACTION_ID)) {
                continue;
            }
            ledger.entries.put(entryTag.getUUID(TAG_FACTION_ID), FactionScience.load(entryTag));
        }
        if (FORMAT.outdated(saved)) {
            ledger.setDirty();
        }
        return ledger;
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static final class FactionScience {
        private final Set<String> discoveries = new LinkedHashSet<>();
        private final Set<UUID> notified = new LinkedHashSet<>();
        private long epochDay = Long.MIN_VALUE;
        private long granted;

        private void rollOver(long today) {
            if (epochDay == today) {
                return;
            }
            epochDay = today;
            granted = 0L;
            notified.clear();
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_EPOCH_DAY, epochDay);
            tag.putLong(TAG_GRANTED, granted);
            ListTag discoveriesTag = new ListTag();
            discoveries.stream().sorted().forEach(id -> discoveriesTag.add(StringTag.valueOf(id)));
            tag.put(TAG_DISCOVERIES, discoveriesTag);
            ListTag notifiedTag = new ListTag();
            notified.stream().sorted(Comparator.comparing(UUID::toString)).forEach(playerId -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(TAG_PLAYER, playerId);
                notifiedTag.add(playerTag);
            });
            tag.put(TAG_NOTIFIED, notifiedTag);
            return tag;
        }

        private static FactionScience load(CompoundTag tag) {
            FactionScience entry = new FactionScience();
            entry.epochDay = tag.contains(TAG_EPOCH_DAY, Tag.TAG_LONG) ? tag.getLong(TAG_EPOCH_DAY) : Long.MIN_VALUE;
            entry.granted = Math.max(0L, tag.getLong(TAG_GRANTED));
            ListTag discoveriesTag = tag.getList(TAG_DISCOVERIES, Tag.TAG_STRING);
            for (int index = 0; index < discoveriesTag.size(); index++) {
                ResourceLocation itemId = ResourceLocation.tryParse(discoveriesTag.getString(index));
                if (itemId != null) {
                    entry.discoveries.add(itemId.toString());
                }
            }
            ListTag notifiedTag = tag.getList(TAG_NOTIFIED, Tag.TAG_COMPOUND);
            for (int index = 0; index < notifiedTag.size(); index++) {
                CompoundTag playerTag = notifiedTag.getCompound(index);
                if (playerTag.hasUUID(TAG_PLAYER)) {
                    entry.notified.add(playerTag.getUUID(TAG_PLAYER));
                }
            }
            return entry;
        }
    }
}
