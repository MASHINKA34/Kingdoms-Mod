package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.claim.ClaimKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

public final class DungeonProtection {
    public static boolean isDungeon(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && isDungeon(serverLevel, new ChunkPos(pos));
    }

    public static boolean isDungeon(LevelReader level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && isDungeon(serverLevel, new ChunkPos(pos));
    }

    public static boolean isDungeon(ServerLevel level, ChunkPos chunk) {
        DungeonManager manager = DungeonManager.get(level);
        return !manager.isEmpty() && manager.isDungeon(new ClaimKey(level.dimension(), chunk));
    }

    public static boolean blocksFire(LevelReader level, BlockPos pos) {
        return isDungeon(level, pos);
    }

    public static boolean nearDungeon(ServerLevel level, BlockPos pos, int radiusBlocks) {
        int minChunkX = (pos.getX() - radiusBlocks) >> 4;
        int maxChunkX = (pos.getX() + radiusBlocks) >> 4;
        int minChunkZ = (pos.getZ() - radiusBlocks) >> 4;
        int maxChunkZ = (pos.getZ() + radiusBlocks) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (isDungeon(level, new ChunkPos(chunkX, chunkZ))) {
                    return true;
                }
            }
        }
        return false;
    }

    private DungeonProtection() {
    }
}
