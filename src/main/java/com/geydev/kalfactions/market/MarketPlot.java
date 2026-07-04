package com.geydev.kalfactions.market;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class MarketPlot {
    private static final String TAG_ID = "id";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_BOX = "box";
    private static final String TAG_BASE_PRICE = "base_price";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_OWNER_NAME = "owner_name";
    private static final String TAG_RESALE_PRICE = "resale_price";
    private static final String TAG_SNAPSHOT = "snapshot";
    private static final String TAG_TRUSTED_PLAYERS = "trusted_players";
    private static final String TAG_TRUSTED_FACTIONS = "trusted_factions";
    private static final String TAG_ENTRY_ID = "id";
    private static final String TAG_ENTRY_NAME = "name";

    private final int id;
    private final ResourceKey<Level> dimension;
    private final BoundingBox box;
    private long basePrice;
    private UUID owner;
    private String ownerName = "";
    private long resalePrice;
    private CompoundTag snapshot = new CompoundTag();
    private final Map<UUID, String> trustedPlayers = new LinkedHashMap<>();
    private final Set<UUID> trustedFactions = new LinkedHashSet<>();

    public MarketPlot(int id, ResourceKey<Level> dimension, BoundingBox box, long basePrice) {
        this.id = id;
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.box = Objects.requireNonNull(box, "box");
        this.basePrice = basePrice;
    }

    public int id() {
        return id;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BoundingBox box() {
        return box;
    }

    public long basePrice() {
        return basePrice;
    }

    public void setBasePrice(long basePrice) {
        this.basePrice = basePrice;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public long resalePrice() {
        return resalePrice;
    }

    public CompoundTag snapshot() {
        return snapshot;
    }

    public void setSnapshot(CompoundTag snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public void setOwner(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = owner == null || ownerName == null ? "" : ownerName;
        this.resalePrice = 0L;
        this.trustedPlayers.clear();
        this.trustedFactions.clear();
    }

    public Map<UUID, String> trustedPlayers() {
        return Map.copyOf(trustedPlayers);
    }

    public Set<UUID> trustedFactions() {
        return Set.copyOf(trustedFactions);
    }

    public boolean isTrustedPlayer(UUID playerId) {
        return trustedPlayers.containsKey(playerId);
    }

    public boolean isTrustedFaction(UUID factionId) {
        return trustedFactions.contains(factionId);
    }

    public boolean addTrustedPlayer(UUID playerId, String name) {
        return trustedPlayers.put(playerId, name == null ? "" : name) == null;
    }

    public boolean removeTrustedPlayer(UUID playerId) {
        return trustedPlayers.remove(playerId) != null;
    }

    public boolean addTrustedFaction(UUID factionId) {
        return trustedFactions.add(factionId);
    }

    public boolean removeTrustedFaction(UUID factionId) {
        return trustedFactions.remove(factionId);
    }

    public void setResalePrice(long resalePrice) {
        this.resalePrice = Math.max(0L, resalePrice);
    }

    public State state() {
        if (owner == null) {
            return State.FOR_SALE;
        }
        return resalePrice > 0L ? State.RESALE : State.OWNED;
    }

    public long askingPrice() {
        return owner == null ? basePrice : resalePrice;
    }

    public boolean isOwnedBy(UUID playerId) {
        return owner != null && owner.equals(playerId);
    }

    public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
        return this.dimension.equals(dimension) && box.isInside(pos);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_ID, id);
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        tag.putIntArray(TAG_BOX, new int[] {
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ()
        });
        tag.putLong(TAG_BASE_PRICE, basePrice);
        if (owner != null) {
            tag.putUUID(TAG_OWNER, owner);
            tag.putString(TAG_OWNER_NAME, ownerName);
        }
        tag.putLong(TAG_RESALE_PRICE, resalePrice);
        tag.put(TAG_SNAPSHOT, snapshot);
        ListTag playersTag = new ListTag();
        for (Map.Entry<UUID, String> entry : trustedPlayers.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_ENTRY_ID, entry.getKey());
            entryTag.putString(TAG_ENTRY_NAME, entry.getValue());
            playersTag.add(entryTag);
        }
        tag.put(TAG_TRUSTED_PLAYERS, playersTag);
        ListTag factionsTag = new ListTag();
        for (UUID factionId : trustedFactions) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(TAG_ENTRY_ID, factionId);
            factionsTag.add(entryTag);
        }
        tag.put(TAG_TRUSTED_FACTIONS, factionsTag);
        return tag;
    }

    public static Optional<MarketPlot> load(CompoundTag tag) {
        ResourceLocation location = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        int[] corners = tag.getIntArray(TAG_BOX);
        if (location == null || corners.length != 6) {
            return Optional.empty();
        }
        MarketPlot plot = new MarketPlot(
                tag.getInt(TAG_ID),
                ResourceKey.create(Registries.DIMENSION, location),
                new BoundingBox(corners[0], corners[1], corners[2], corners[3], corners[4], corners[5]),
                tag.getLong(TAG_BASE_PRICE)
        );
        if (tag.hasUUID(TAG_OWNER)) {
            plot.owner = tag.getUUID(TAG_OWNER);
            plot.ownerName = tag.getString(TAG_OWNER_NAME);
        }
        plot.resalePrice = Math.max(0L, tag.getLong(TAG_RESALE_PRICE));
        if (tag.contains(TAG_SNAPSHOT, Tag.TAG_COMPOUND)) {
            plot.snapshot = tag.getCompound(TAG_SNAPSHOT);
        }
        ListTag playersTag = tag.getList(TAG_TRUSTED_PLAYERS, Tag.TAG_COMPOUND);
        for (int index = 0; index < playersTag.size(); index++) {
            CompoundTag entryTag = playersTag.getCompound(index);
            if (entryTag.hasUUID(TAG_ENTRY_ID)) {
                plot.trustedPlayers.put(entryTag.getUUID(TAG_ENTRY_ID), entryTag.getString(TAG_ENTRY_NAME));
            }
        }
        ListTag factionsTag = tag.getList(TAG_TRUSTED_FACTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < factionsTag.size(); index++) {
            CompoundTag entryTag = factionsTag.getCompound(index);
            if (entryTag.hasUUID(TAG_ENTRY_ID)) {
                plot.trustedFactions.add(entryTag.getUUID(TAG_ENTRY_ID));
            }
        }
        return Optional.of(plot);
    }

    public enum State {
        FOR_SALE,
        OWNED,
        RESALE
    }
}
