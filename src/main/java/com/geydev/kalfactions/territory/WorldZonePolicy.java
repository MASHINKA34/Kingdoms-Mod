package com.geydev.kalfactions.territory;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class WorldZonePolicy {
    public static ResourceZone zoneAt(ServerLevel level, double x, double z) {
        BlockPos spawn = level.getSharedSpawnPos();
        return ResourceZone.at(
                x,
                z,
                spawn.getX(),
                spawn.getZ(),
                ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt(),
                ModConfigSpec.RESOURCE_YELLOW_RADIUS.getAsInt(),
                ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt()
        );
    }

    public static ResourceZone zoneAt(ServerLevel level, BlockPos pos) {
        return zoneAt(level, pos.getX(), pos.getZ());
    }

    public static ResourceZone zoneAt(ServerLevel level, ChunkPos chunk) {
        return zoneAt(level, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
    }

    public static boolean isBlack(ServerLevel level, BlockPos pos) {
        return level.dimension().equals(Level.OVERWORLD) && zoneAt(level, pos) == ResourceZone.BLACK;
    }

    public static boolean isBlack(ServerLevel level, ChunkPos chunk) {
        return level.dimension().equals(Level.OVERWORLD) && zoneAt(level, chunk) == ResourceZone.BLACK;
    }

    public static boolean isBlack(MinecraftServer server, ClaimKey key) {
        if (!key.dimension().equals(Level.OVERWORLD)) {
            return false;
        }
        ServerLevel level = server.getLevel(key.dimension());
        return level != null && isBlack(level, key.chunk());
    }

    private WorldZonePolicy() {
    }
}
