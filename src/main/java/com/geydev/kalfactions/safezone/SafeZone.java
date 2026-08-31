package com.geydev.kalfactions.safezone;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record SafeZone(String id, ResourceKey<Level> dimension, AABB box) {
    private static final String TAG_ID = "id";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_MIN = "min";
    private static final String TAG_MAX = "max";

    public SafeZone {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(box, "box");
    }

    public static SafeZone of(String id, ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
        BoundingBox bounds = BoundingBox.fromCorners(first, second);
        return new SafeZone(id, dimension, new AABB(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX() + 1,
                bounds.maxY() + 1,
                bounds.maxZ() + 1
        ));
    }

    public BlockPos min() {
        return BlockPos.containing(box.minX, box.minY, box.minZ);
    }

    public BlockPos max() {
        return BlockPos.containing(box.maxX - 1.0D, box.maxY - 1.0D, box.maxZ - 1.0D);
    }

    public boolean contains(ResourceKey<Level> otherDimension, Vec3 position) {
        return dimension.equals(otherDimension) && box.contains(position);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ID, id);
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        BlockPos min = min();
        BlockPos max = max();
        tag.putIntArray(TAG_MIN, new int[] {min.getX(), min.getY(), min.getZ()});
        tag.putIntArray(TAG_MAX, new int[] {max.getX(), max.getY(), max.getZ()});
        return tag;
    }

    public static Optional<SafeZone> load(CompoundTag tag) {
        String id = SafeZoneManager.normalizeId(tag.getString(TAG_ID));
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        int[] min = tag.getIntArray(TAG_MIN);
        int[] max = tag.getIntArray(TAG_MAX);
        if (!SafeZoneManager.isValidId(id) || dimensionId == null || min.length != 3 || max.length != 3) {
            return Optional.empty();
        }
        return Optional.of(of(
                id,
                ResourceKey.create(Registries.DIMENSION, dimensionId),
                new BlockPos(min[0], min[1], min[2]),
                new BlockPos(max[0], max[1], max[2])
        ));
    }
}
