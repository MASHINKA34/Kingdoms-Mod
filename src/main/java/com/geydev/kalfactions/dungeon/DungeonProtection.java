package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.claim.ClaimKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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

    public static boolean blocksChanges(Level level, BlockPos pos) {
        return isDungeon(level, pos);
    }

    public static boolean blocksChanges(Level level, BlockPos pos, Entity source) {
        if (!isDungeon(level, pos)) {
            return false;
        }
        return !(source instanceof ServerPlayer player) || !player.hasPermissions(2);
    }

    public static boolean blocksPlayer(ServerPlayer player, Level level, BlockPos pos) {
        return !player.hasPermissions(2) && isDungeon(level, pos);
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

    public static String nameAt(ServerLevel level, BlockPos pos) {
        return DungeonManager.get(level)
                .dungeonAt(ClaimKey.of(level, pos))
                .map(DungeonManager.DungeonView::name)
                .orElse("");
    }

    private DungeonProtection() {
    }
}
