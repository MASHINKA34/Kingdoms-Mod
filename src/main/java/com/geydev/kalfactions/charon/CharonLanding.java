package com.geydev.kalfactions.charon;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public final class CharonLanding {
    private static final int MAX_RADIUS = 3;
    private static final int MAX_CLIMB = 16;
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2};

    public static BlockPos find(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= MAX_RADIUS; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.abs(offsetX) != radius && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    BlockPos standing = standingSpot(level, origin.offset(offsetX, 0, offsetZ));
                    if (standing != null) {
                        return standing;
                    }
                }
            }
        }
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int step = 0; step < MAX_CLIMB && cursor.getY() < level.getMaxBuildHeight() - 1; step++) {
            if (isFree(level, cursor) && isFree(level, cursor.above())) {
                return cursor.immutable();
            }
            cursor.move(Direction.UP);
        }
        return origin.immutable();
    }

    @Nullable
    private static BlockPos standingSpot(ServerLevel level, BlockPos column) {
        for (int offsetY : HEIGHTS) {
            BlockPos feet = column.above(offsetY);
            if (feet.getY() <= level.getMinBuildHeight()
                    || feet.getY() >= level.getMaxBuildHeight() - 1
                    || !level.getWorldBorder().isWithinBounds(feet)) {
                continue;
            }
            if (isFree(level, feet) && isFree(level, feet.above()) && isStandable(level, feet.below())) {
                return feet;
            }
        }
        return null;
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private CharonLanding() {
    }
}
