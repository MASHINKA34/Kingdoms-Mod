package com.geydev.kalfactions.claim;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record ClaimKey(ResourceKey<Level> dimension, ChunkPos chunk) implements Comparable<ClaimKey> {
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CHUNK = "chunk";

    public ClaimKey {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(chunk, "chunk");
        chunk = new ChunkPos(chunk.x, chunk.z);
    }

    public ClaimKey(ResourceKey<Level> dimension, int x, int z) {
        this(dimension, new ChunkPos(x, z));
    }

    public static ClaimKey of(Level level, ChunkPos chunk) {
        return new ClaimKey(level.dimension(), chunk);
    }

    public static ClaimKey of(Level level, BlockPos position) {
        return new ClaimKey(level.dimension(), new ChunkPos(position));
    }

    public int x() {
        return chunk.x;
    }

    public int z() {
        return chunk.z;
    }

    public ClaimKey offset(int xOffset, int zOffset) {
        return new ClaimKey(dimension, x() + xOffset, z() + zOffset);
    }

    public List<ClaimKey> cardinalNeighbors() {
        return List.of(offset(1, 0), offset(-1, 0), offset(0, 1), offset(0, -1));
    }

    public boolean isCardinalNeighbor(ClaimKey other) {
        if (!dimension.equals(other.dimension)) {
            return false;
        }
        return Math.abs(x() - other.x()) + Math.abs(z() - other.z()) == 1;
    }

    public boolean contains(BlockPos position) {
        return ChunkPos.asLong(position) == chunk.toLong();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        tag.putLong(TAG_CHUNK, chunk.toLong());
        return tag;
    }

    public static Optional<ClaimKey> load(CompoundTag tag) {
        ResourceLocation location = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (location == null || !tag.contains(TAG_CHUNK, net.minecraft.nbt.Tag.TAG_LONG)) {
            return Optional.empty();
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, location);
        return Optional.of(new ClaimKey(dimension, new ChunkPos(tag.getLong(TAG_CHUNK))));
    }

    @Override
    public int compareTo(ClaimKey other) {
        int dimensionOrder = dimension.location().compareTo(other.dimension.location());
        if (dimensionOrder != 0) {
            return dimensionOrder;
        }
        int xOrder = Integer.compare(x(), other.x());
        return xOrder != 0 ? xOrder : Integer.compare(z(), other.z());
    }
}
