package com.geydev.kalfactions.tax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class OfflineNoticeQueue extends SavedData {
    public static final String DATA_NAME = "kingdoms_offline_notices";
    public static final Factory<OfflineNoticeQueue> FACTORY =
        new Factory<>(OfflineNoticeQueue::new, OfflineNoticeQueue::load);

    private static final int MAX_PER_PLAYER = 20;
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_PLAYER_ID = "id";
    private static final String TAG_NOTICES = "notices";
    private static final String TAG_MESSAGE = "message";
    private static final String TAG_SUCCESSFUL = "successful";

    public record StoredNotice(String messageJson, boolean successful) {
    }

    private final Map<UUID, List<StoredNotice>> notices = new HashMap<>();

    public static OfflineNoticeQueue get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized void enqueue(MinecraftServer server, UUID playerId, Component message, boolean successful) {
        String json = Component.Serializer.toJson(message, server.registryAccess());
        List<StoredNotice> queue = notices.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        if (queue.size() >= MAX_PER_PLAYER) {
            queue.removeFirst();
        }
        queue.add(new StoredNotice(json, successful));
        setDirty();
    }

    public synchronized List<StoredNotice> drain(UUID playerId) {
        List<StoredNotice> queue = notices.remove(playerId);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        setDirty();
        return List.copyOf(queue);
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playersTag = new ListTag();
        notices.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID(TAG_PLAYER_ID, entry.getKey());
                ListTag noticesTag = new ListTag();
                for (StoredNotice notice : entry.getValue()) {
                    CompoundTag noticeTag = new CompoundTag();
                    noticeTag.putString(TAG_MESSAGE, notice.messageJson());
                    noticeTag.putBoolean(TAG_SUCCESSFUL, notice.successful());
                    noticesTag.add(noticeTag);
                }
                playerTag.put(TAG_NOTICES, noticesTag);
                playersTag.add(playerTag);
            });
        tag.put(TAG_PLAYERS, playersTag);
        return tag;
    }

    private static OfflineNoticeQueue load(CompoundTag tag, HolderLookup.Provider registries) {
        OfflineNoticeQueue queue = new OfflineNoticeQueue();
        ListTag playersTag = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < playersTag.size(); index++) {
            CompoundTag playerTag = playersTag.getCompound(index);
            if (!playerTag.hasUUID(TAG_PLAYER_ID)) {
                continue;
            }
            List<StoredNotice> stored = new ArrayList<>();
            ListTag noticesTag = playerTag.getList(TAG_NOTICES, Tag.TAG_COMPOUND);
            for (int noticeIndex = 0; noticeIndex < noticesTag.size(); noticeIndex++) {
                CompoundTag noticeTag = noticesTag.getCompound(noticeIndex);
                stored.add(new StoredNotice(noticeTag.getString(TAG_MESSAGE), noticeTag.getBoolean(TAG_SUCCESSFUL)));
            }
            if (!stored.isEmpty()) {
                queue.notices.put(playerTag.getUUID(TAG_PLAYER_ID), stored);
            }
        }
        return queue;
    }
}
