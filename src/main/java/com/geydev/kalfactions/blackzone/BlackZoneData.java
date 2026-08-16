package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class BlackZoneData extends SavedData {
    public static final String DATA_NAME = "kingdoms_black_zone";
    public static final Factory<BlackZoneData> FACTORY =
        new Factory<>(BlackZoneData::new, BlackZoneData::load);

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER_ID = "id";
    private static final String TAG_ACCUMULATED = "accumulated";
    private static final String TAG_LAST_IN_ZONE = "lastInZone";
    private static final String TAG_NOTIFIED_STAGE = "notifiedStage";
    private static final String TAG_LAST_KOLYVAN = "lastKolyvan";

    private final Map<UUID, BlackZoneToll> tolls = new HashMap<>();

    public static BlackZoneData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized BlackZoneToll toll(UUID playerId) {
        return tolls.getOrDefault(playerId, BlackZoneToll.EMPTY);
    }

    public synchronized void put(UUID playerId, BlackZoneToll toll) {
        BlackZoneToll previous = tolls.get(playerId);
        if (toll == null || toll.equals(BlackZoneToll.EMPTY)) {
            if (tolls.remove(playerId) != null) {
                setDirty();
            }
            return;
        }
        if (toll.equals(previous)) {
            return;
        }
        tolls.put(playerId, toll);
        setDirty();
    }

    public synchronized void clear(UUID playerId) {
        if (tolls.remove(playerId) != null) {
            setDirty();
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag playersTag = new ListTag();
        tolls.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(TAG_PLAYER_ID, entry.getKey());
                playerTag.putLong(TAG_ACCUMULATED, entry.getValue().accumulatedMillis());
                playerTag.putLong(TAG_LAST_IN_ZONE, entry.getValue().lastInZoneAt());
                playerTag.putInt(TAG_NOTIFIED_STAGE, entry.getValue().notifiedStage());
                playerTag.putLong(TAG_LAST_KOLYVAN, entry.getValue().lastKolyvanAt());
                playersTag.add(playerTag);
            });
        tag.put(TAG_PLAYERS, playersTag);
        return tag;
    }

    private static BlackZoneData load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        boolean legacyFormat = FORMAT.outdated(saved);
        BlackZoneData data = new BlackZoneData();
        ListTag playersTag = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < playersTag.size(); index++) {
            CompoundTag playerTag = playersTag.getCompound(index);
            if (!playerTag.hasUUID(TAG_PLAYER_ID)) {
                continue;
            }
            data.tolls.put(playerTag.getUUID(TAG_PLAYER_ID), new BlackZoneToll(
                playerTag.getLong(TAG_ACCUMULATED),
                playerTag.getLong(TAG_LAST_IN_ZONE),
                playerTag.contains(TAG_NOTIFIED_STAGE) ? playerTag.getInt(TAG_NOTIFIED_STAGE) : -1,
                playerTag.getLong(TAG_LAST_KOLYVAN)
            ));
        }
        if (legacyFormat) {
            data.setDirty();
        }
        return data;
    }
}
