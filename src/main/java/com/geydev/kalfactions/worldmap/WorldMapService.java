package com.geydev.kalfactions.worldmap;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WorldMapService {
    private WorldMapService() {
    }

    public static void send(ServerPlayer player) {
        MinecraftServer server = player.server;
        WorldMapStorage.Meta meta = WorldMapStorage.meta(server).orElse(null);
        if (meta == null || !WorldMapStorage.exists(server)) {
            PacketDistributor.sendToPlayer(player, unavailable());
            return;
        }
        byte[] bytes;
        try {
            bytes = WorldMapStorage.readImageBytes(server);
        } catch (IOException e) {
            KalFactions.LOGGER.error("Failed to read world map image", e);
            PacketDistributor.sendToPlayer(player, unavailable());
            return;
        }
        int maxPart = FactionPayloads.S2CWorldMapPart.MAX_PART;
        int totalParts = (bytes.length + maxPart - 1) / maxPart;
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CWorldMapBegin(
                meta.resolution(), meta.resolution(), meta.centerX(), meta.centerZ(),
                meta.regionBlocks(), totalParts, bytes.length, meta.version()));
        for (int i = 0; i < totalParts; i++) {
            int from = i * maxPart;
            int to = Math.min(bytes.length, from + maxPart);
            PacketDistributor.sendToPlayer(player,
                    new FactionPayloads.S2CWorldMapPart(meta.version(), i, Arrays.copyOfRange(bytes, from, to)));
        }
        WorldMapTrackSync.send(player, meta);
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private static FactionPayloads.S2CWorldMapBegin unavailable() {
        return new FactionPayloads.S2CWorldMapBegin(0, 0, 0, 0, 0, 0, 0, 0L);
    }
}
