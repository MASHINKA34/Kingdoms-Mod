package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public final class TraderSpawnSafety {
    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (level.getWorldBorder().getDistanceToBorder(pos.getX() + 0.5D, pos.getZ() + 0.5D)
                < ModConfigSpec.TRADER_WORLD_BORDER_MARGIN.getAsInt()) {
            return false;
        }
        BlockPos floor = pos.below();
        if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                || level.getBlockEntity(floor) != null
                || level.getBlockEntity(pos) != null
                || level.getBlockEntity(pos.above()) != null) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
            return false;
        }
        if (dangerous(level, floor) || dangerous(level, pos) || dangerous(level, pos.above())) {
            return false;
        }
        AABB body = new AABB(
                pos.getX() + 0.2D, pos.getY(), pos.getZ() + 0.2D,
                pos.getX() + 0.8D, pos.getY() + 1.95D, pos.getZ() + 0.8D
        );
        return level.noCollision(body) && level.getEntities(null, body).isEmpty();
    }

    private static boolean dangerous(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.LAVA)
                || level.getBlockState(pos).is(Blocks.FIRE)
                || level.getBlockState(pos).is(Blocks.SOUL_FIRE)
                || level.getBlockState(pos).is(Blocks.MAGMA_BLOCK)
                || level.getBlockState(pos).is(Blocks.CACTUS)
                || level.getBlockState(pos).is(Blocks.CAMPFIRE)
                || level.getBlockState(pos).is(Blocks.SOUL_CAMPFIRE)
                || level.getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH)
                || level.getBlockState(pos).is(Blocks.POWDER_SNOW);
    }

    private TraderSpawnSafety() {
    }
}
