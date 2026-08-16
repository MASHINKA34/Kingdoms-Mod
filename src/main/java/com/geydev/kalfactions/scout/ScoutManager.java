package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.data.SavedDataFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class ScoutManager extends SavedData {
    public static final String DATA_NAME = "kingdoms_scout";
    public static final Factory<ScoutManager> FACTORY = new Factory<>(ScoutManager::new, ScoutManager::load);

    private static final SavedDataFormat FORMAT = new SavedDataFormat(1);
    private static final String TAG_ORDERS = "orders";
    private static final String TAG_FACTION_ID = "faction";
    private static final String TAG_ORDER = "order";
    private static final String TAG_POSTS = "posts";
    private static final String TAG_POST_ID = "id";
    private static final String TAG_POST_DIMENSION = "dimension";
    private static final String TAG_POST_POS = "pos";

    private final Map<UUID, ScoutOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, ScoutPost> posts = new LinkedHashMap<>();

    public record ScoutPost(UUID id, ResourceKey<Level> dimension, BlockPos pos) {
    }

    public static ScoutManager get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized Optional<ScoutOrder> activeOrder(UUID factionId) {
        ScoutOrder order = orders.get(factionId);
        return order == null || order.delivered() ? Optional.empty() : Optional.of(order);
    }

    public synchronized Optional<ScoutOrder> order(UUID factionId) {
        return Optional.ofNullable(orders.get(factionId));
    }

    public synchronized boolean hasActiveOrder(UUID factionId) {
        return activeOrder(factionId).isPresent();
    }

    public synchronized List<Map.Entry<UUID, ScoutOrder>> activeOrders() {
        List<Map.Entry<UUID, ScoutOrder>> active = new ArrayList<>();
        for (Map.Entry<UUID, ScoutOrder> entry : orders.entrySet()) {
            if (!entry.getValue().delivered()) {
                active.add(Map.entry(entry.getKey(), entry.getValue()));
            }
        }
        return active;
    }

    public synchronized boolean startOrder(UUID factionId, ScoutOrder order) {
        if (hasActiveOrder(factionId)) {
            return false;
        }
        orders.put(factionId, order);
        setDirty();
        return true;
    }

    public synchronized void removeOrder(UUID factionId) {
        if (orders.remove(factionId) != null) {
            setDirty();
        }
    }

    public synchronized void markDirty() {
        setDirty();
    }

    public synchronized List<ScoutPost> posts() {
        return posts.values().stream().sorted(Comparator.comparing(post -> post.id().toString())).toList();
    }

    public synchronized void addPost(UUID id, ResourceKey<Level> dimension, BlockPos pos) {
        posts.put(id, new ScoutPost(id, dimension, pos.immutable()));
        setDirty();
    }

    public synchronized void removePost(UUID id) {
        if (posts.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        FORMAT.stamp(tag);
        ListTag ordersTag = new ListTag();
        orders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .forEach(entry -> {
                    CompoundTag entryTag = new CompoundTag();
                    entryTag.putUUID(TAG_FACTION_ID, entry.getKey());
                    entryTag.put(TAG_ORDER, entry.getValue().save());
                    ordersTag.add(entryTag);
                });
        tag.put(TAG_ORDERS, ordersTag);

        ListTag postsTag = new ListTag();
        posts.values().stream()
                .sorted(Comparator.comparing(post -> post.id().toString()))
                .forEach(post -> {
                    CompoundTag postTag = new CompoundTag();
                    postTag.putUUID(TAG_POST_ID, post.id());
                    postTag.putString(TAG_POST_DIMENSION, post.dimension().location().toString());
                    postTag.putLong(TAG_POST_POS, post.pos().asLong());
                    postsTag.add(postTag);
                });
        tag.put(TAG_POSTS, postsTag);
        return tag;
    }

    private static ScoutManager load(CompoundTag saved, HolderLookup.Provider registries) {
        CompoundTag tag = FORMAT.upgrade(saved);
        boolean legacyFormat = FORMAT.outdated(saved);
        ScoutManager manager = new ScoutManager();
        ListTag ordersTag = tag.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < ordersTag.size(); index++) {
            CompoundTag entryTag = ordersTag.getCompound(index);
            if (!entryTag.hasUUID(TAG_FACTION_ID)) {
                continue;
            }
            ScoutOrder order = ScoutOrder.load(entryTag.getCompound(TAG_ORDER), registries);
            if (order != null) {
                manager.orders.put(entryTag.getUUID(TAG_FACTION_ID), order);
            }
        }
        ListTag postsTag = tag.getList(TAG_POSTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < postsTag.size(); index++) {
            CompoundTag postTag = postsTag.getCompound(index);
            if (!postTag.hasUUID(TAG_POST_ID)) {
                continue;
            }
            ResourceLocation dimensionId = ResourceLocation.tryParse(postTag.getString(TAG_POST_DIMENSION));
            if (dimensionId == null) {
                continue;
            }
            UUID id = postTag.getUUID(TAG_POST_ID);
            manager.posts.put(id, new ScoutPost(
                    id,
                    ResourceKey.create(Registries.DIMENSION, dimensionId),
                    BlockPos.of(postTag.getLong(TAG_POST_POS))
            ));
        }
        if (legacyFormat) {
            manager.setDirty();
        }
        return manager;
    }
}
