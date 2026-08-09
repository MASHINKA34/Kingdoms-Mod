package com.geydev.kalfactions.scout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ScoutOrder {
    private static final String TAG_ID = "id";
    private static final String TAG_PACKAGE = "package";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CENTER_X = "centerChunkX";
    private static final String TAG_CENTER_Z = "centerChunkZ";
    private static final String TAG_SIZE = "sizeChunks";
    private static final String TAG_START = "startMillis";
    private static final String TAG_DURATION = "durationMillis";
    private static final String TAG_PAID = "paid";
    private static final String TAG_ORDERER = "orderer";
    private static final String TAG_SCANNED = "scanned";
    private static final String TAG_DELIVERED = "delivered";
    private static final String TAG_STAGED = "stagedRegions";

    private final UUID id;
    private final ScoutPackage option;
    private final ResourceKey<Level> dimension;
    private final int centerChunkX;
    private final int centerChunkZ;
    private final int sizeChunks;
    private final long startMillis;
    private final long durationMillis;
    private final long paid;
    private final UUID ordererId;
    private final List<String> stagedRegions = new ArrayList<>();
    private boolean scanned;
    private boolean delivered;
    private transient int cursor;
    private transient boolean deliveryInFlight;

    public ScoutOrder(
            UUID id,
            ScoutPackage option,
            ResourceKey<Level> dimension,
            int centerChunkX,
            int centerChunkZ,
            int sizeChunks,
            long startMillis,
            long durationMillis,
            long paid,
            UUID ordererId
    ) {
        this.id = id;
        this.option = option;
        this.dimension = dimension;
        this.centerChunkX = centerChunkX;
        this.centerChunkZ = centerChunkZ;
        this.sizeChunks = Math.max(1, sizeChunks);
        this.startMillis = startMillis;
        this.durationMillis = Math.max(0L, durationMillis);
        this.paid = Math.max(0L, paid);
        this.ordererId = ordererId;
    }

    public UUID id() {
        return id;
    }

    public ScoutPackage option() {
        return option;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public int centerChunkX() {
        return centerChunkX;
    }

    public int centerChunkZ() {
        return centerChunkZ;
    }

    public int sizeChunks() {
        return sizeChunks;
    }

    public int minChunkX() {
        return centerChunkX - (sizeChunks - 1) / 2;
    }

    public int minChunkZ() {
        return centerChunkZ - (sizeChunks - 1) / 2;
    }

    public int chunkCount() {
        return sizeChunks * sizeChunks;
    }

    public long startMillis() {
        return startMillis;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public long endsAtMillis() {
        return startMillis + durationMillis;
    }

    public long remainingMillis(long nowMillis) {
        return Math.max(0L, endsAtMillis() - nowMillis);
    }

    public boolean timeElapsed(long nowMillis) {
        return nowMillis >= endsAtMillis();
    }

    public long paid() {
        return paid;
    }

    public UUID ordererId() {
        return ordererId;
    }

    public int cursor() {
        return cursor;
    }

    public void setCursor(int value) {
        cursor = Math.max(0, Math.min(chunkCount(), value));
    }

    public int progressPercent() {
        if (scanned) {
            return 100;
        }
        return (int) (100L * cursor / Math.max(1, chunkCount()));
    }

    public boolean scanned() {
        return scanned;
    }

    public void markScanned(List<String> regions) {
        scanned = true;
        stagedRegions.clear();
        stagedRegions.addAll(regions);
    }

    public List<String> stagedRegions() {
        return List.copyOf(stagedRegions);
    }

    public boolean delivered() {
        return delivered;
    }

    public void markDelivered() {
        delivered = true;
        deliveryInFlight = false;
    }

    public boolean deliveryInFlight() {
        return deliveryInFlight;
    }

    public void setDeliveryInFlight(boolean value) {
        deliveryInFlight = value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_PACKAGE, option.id());
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        tag.putInt(TAG_CENTER_X, centerChunkX);
        tag.putInt(TAG_CENTER_Z, centerChunkZ);
        tag.putInt(TAG_SIZE, sizeChunks);
        tag.putLong(TAG_START, startMillis);
        tag.putLong(TAG_DURATION, durationMillis);
        tag.putLong(TAG_PAID, paid);
        if (ordererId != null) {
            tag.putUUID(TAG_ORDERER, ordererId);
        }
        tag.putBoolean(TAG_SCANNED, scanned);
        tag.putBoolean(TAG_DELIVERED, delivered);
        ListTag regionsTag = new ListTag();
        for (String region : stagedRegions) {
            regionsTag.add(net.minecraft.nbt.StringTag.valueOf(region));
        }
        tag.put(TAG_STAGED, regionsTag);
        return tag;
    }

    public static ScoutOrder load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID(TAG_ID)) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimensionId == null) {
            return null;
        }
        ScoutPackage option = ScoutPackage.byId(tag.getString(TAG_PACKAGE)).orElse(ScoutPackage.SMALL);
        ScoutOrder order = new ScoutOrder(
                tag.getUUID(TAG_ID),
                option,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                tag.getInt(TAG_CENTER_X),
                tag.getInt(TAG_CENTER_Z),
                tag.getInt(TAG_SIZE),
                tag.getLong(TAG_START),
                tag.getLong(TAG_DURATION),
                tag.getLong(TAG_PAID),
                tag.hasUUID(TAG_ORDERER) ? tag.getUUID(TAG_ORDERER) : null
        );
        order.scanned = tag.getBoolean(TAG_SCANNED);
        order.delivered = tag.getBoolean(TAG_DELIVERED);
        ListTag regionsTag = tag.getList(TAG_STAGED, Tag.TAG_STRING);
        for (int index = 0; index < regionsTag.size(); index++) {
            order.stagedRegions.add(regionsTag.getString(index));
        }
        return order;
    }
}
