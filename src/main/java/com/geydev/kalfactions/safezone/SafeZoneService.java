package com.geydev.kalfactions.safezone;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SafeZoneService {
    public static void syncTo(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        List<SafeZonePayloads.ZoneEntry> entries = new ArrayList<>();
        for (SafeZone zone : SafeZoneManager.get(player.serverLevel()).all()) {
            if (!zone.dimension().equals(dimension)) {
                continue;
            }
            BlockPos min = zone.min();
            BlockPos max = zone.max();
            entries.add(new SafeZonePayloads.ZoneEntry(
                    zone.id(),
                    min.getX(), min.getY(), min.getZ(),
                    max.getX(), max.getY(), max.getZ()
            ));
        }
        PacketDistributor.sendToPlayer(
                player,
                new SafeZonePayloads.S2CSyncSafeZones(dimension.location(), List.copyOf(entries))
        );
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    private SafeZoneService() {
    }
}
