package com.geydev.kalfactions.profiling;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class LagTaxHooks {
    public static boolean shouldSkipTick(BlockEntity blockEntity) {
        if (FrozenChunks.isEmpty()) {
            return false;
        }
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return false;
        }
        return FrozenChunks.contains(level.dimension(), ChunkPos.asLong(blockEntity.getBlockPos()));
    }

    public static void beginTick(BlockEntity blockEntity) {
        if (ChunkProfiler.sampling()) {
            ChunkProfiler.begin(blockEntity);
        }
    }

    public static void endTick(BlockEntity blockEntity) {
        if (ChunkProfiler.sampling()) {
            ChunkProfiler.end(blockEntity);
        }
    }

    private LagTaxHooks() {
    }
}
