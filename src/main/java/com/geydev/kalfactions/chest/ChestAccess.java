package com.geydev.kalfactions.chest;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ChestAccess {
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POSITION = "position";
    private static final String TAG_FACTION = "faction";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_MODE = "mode";
    private static final String TAG_WHITELIST = "whitelist";

    private final Key key;
    private final UUID factionId;
    private final UUID ownerId;
    private final ChestAccessMode mode;
    private final Set<UUID> whitelistedPlayers;

    public ChestAccess(
        Key key,
        UUID factionId,
        UUID ownerId,
        ChestAccessMode mode,
        Collection<UUID> whitelistedPlayers
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.factionId = Objects.requireNonNull(factionId, "factionId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.mode = Objects.requireNonNull(mode, "mode");
        LinkedHashSet<UUID> whitelist = new LinkedHashSet<>(
            Objects.requireNonNull(whitelistedPlayers, "whitelistedPlayers")
        );
        whitelist.remove(null);
        whitelist.remove(ownerId);
        this.whitelistedPlayers = Set.copyOf(whitelist);
    }

    public ChestAccess(Key key, UUID factionId, UUID ownerId, ChestAccessMode mode) {
        this(key, factionId, ownerId, mode, Set.of());
    }

    public Key key() {
        return key;
    }

    public UUID factionId() {
        return factionId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public ChestAccessMode mode() {
        return mode;
    }

    public Set<UUID> whitelistedPlayers() {
        return whitelistedPlayers;
    }

    public ChestAccess withMode(ChestAccessMode newMode) {
        return new ChestAccess(key, factionId, ownerId, newMode, whitelistedPlayers);
    }

    public ChestAccess withWhitelistedPlayer(UUID playerId) {
        LinkedHashSet<UUID> whitelist = new LinkedHashSet<>(whitelistedPlayers);
        whitelist.add(playerId);
        return new ChestAccess(key, factionId, ownerId, mode, whitelist);
    }

    public ChestAccess withoutWhitelistedPlayer(UUID playerId) {
        LinkedHashSet<UUID> whitelist = new LinkedHashSet<>(whitelistedPlayers);
        whitelist.remove(playerId);
        return new ChestAccess(key, factionId, ownerId, mode, whitelist);
    }

    public boolean canAccess(UUID playerId, UUID playerFactionId) {
        Objects.requireNonNull(playerId, "playerId");
        if (ownerId.equals(playerId) || mode == ChestAccessMode.PUBLIC) {
            return true;
        }
        return switch (mode) {
            case PERSONAL, PUBLIC -> false;
            case FACTION -> factionId.equals(playerFactionId);
            case WHITELIST -> factionId.equals(playerFactionId) || whitelistedPlayers.contains(playerId);
        };
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, key.dimension().location().toString());
        tag.putLong(TAG_POSITION, key.position().asLong());
        tag.putUUID(TAG_FACTION, factionId);
        tag.putUUID(TAG_OWNER, ownerId);
        tag.putString(TAG_MODE, mode.name());

        ListTag whitelist = new ListTag();
        whitelistedPlayers.stream()
            .sorted(Comparator.comparing(UUID::toString))
            .map(UUID::toString)
            .map(StringTag::valueOf)
            .forEach(whitelist::add);
        tag.put(TAG_WHITELIST, whitelist);
        return tag;
    }

    public static Optional<ChestAccess> load(CompoundTag tag) {
        ResourceLocation dimensionLocation = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimensionLocation == null
            || !tag.contains(TAG_POSITION, Tag.TAG_LONG)
            || !tag.hasUUID(TAG_FACTION)
            || !tag.hasUUID(TAG_OWNER)) {
            return Optional.empty();
        }

        ChestAccessMode mode;
        try {
            mode = ChestAccessMode.valueOf(tag.getString(TAG_MODE).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        Set<UUID> whitelistedPlayers = new LinkedHashSet<>();
        ListTag whitelistTag = tag.getList(TAG_WHITELIST, Tag.TAG_STRING);
        for (int index = 0; index < whitelistTag.size(); index++) {
            try {
                whitelistedPlayers.add(UUID.fromString(whitelistTag.getString(index)));
            } catch (IllegalArgumentException ignored) {
                // Ignore only the malformed whitelist entry and preserve the chest rule.
            }
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionLocation);
        Key key = new Key(dimension, BlockPos.of(tag.getLong(TAG_POSITION)));
        return Optional.of(new ChestAccess(
            key,
            tag.getUUID(TAG_FACTION),
            tag.getUUID(TAG_OWNER),
            mode,
            whitelistedPlayers
        ));
    }

    public record Key(ResourceKey<Level> dimension, BlockPos position) implements Comparable<Key> {
        public Key {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(position, "position");
            position = position.immutable();
        }

        public static Key of(Level level, BlockPos position) {
            return new Key(level.dimension(), position);
        }

        @Override
        public int compareTo(Key other) {
            int dimensionOrder = dimension.location().compareTo(other.dimension.location());
            return dimensionOrder != 0 ? dimensionOrder : Long.compare(position.asLong(), other.position.asLong());
        }
    }
}
