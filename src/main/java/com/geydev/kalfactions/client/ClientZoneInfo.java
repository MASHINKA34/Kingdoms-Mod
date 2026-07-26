package com.geydev.kalfactions.client;

import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.worldgen.ZonedOreFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class ClientZoneInfo {
    public static ResourceZone zoneAt(BlockPos position) {
        Minecraft minecraft = Minecraft.getInstance();
        if (position == null || minecraft.level == null) {
            return null;
        }
        if (!minecraft.level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }
        BlockPos spawn = minecraft.level.getSharedSpawnPos();
        try {
            return ZonedOreFeature.zoneAt(position.getX(), position.getZ(), spawn.getX(), spawn.getZ());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return null;
        }
    }

    private ClientZoneInfo() {
    }
}
