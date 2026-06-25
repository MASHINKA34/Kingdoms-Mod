package com.geydev.kalfactions.worldmap;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WorldMapService {
    private WorldMapService() {
    }

    public static void send(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!WorldMapStorage.exists(server)) {
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
        Properties meta = WorldMapStorage.readMeta(server).orElseGet(Properties::new);
        int resolution = parseInt(meta, "resolution", 0);
        int centerX = parseInt(meta, "centerX", 0);
        int centerZ = parseInt(meta, "centerZ", 0);
        int regionBlocks = parseInt(meta, "regionBlocks", 0);
        long version = parseLong(meta, "renderedAt", 0L);
        int maxPart = FactionPayloads.S2CWorldMapPart.MAX_PART;
        int totalParts = (bytes.length + maxPart - 1) / maxPart;
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CWorldMapBegin(
                resolution, resolution, centerX, centerZ, regionBlocks, totalParts, bytes.length, version));
        for (int i = 0; i < totalParts; i++) {
            int from = i * maxPart;
            int to = Math.min(bytes.length, from + maxPart);
            PacketDistributor.sendToPlayer(player,
                    new FactionPayloads.S2CWorldMapPart(version, i, Arrays.copyOfRange(bytes, from, to)));
        }
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    private static FactionPayloads.S2CWorldMapBegin unavailable() {
        return new FactionPayloads.S2CWorldMapBegin(0, 0, 0, 0, 0, 0, 0, 0L);
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(properties.getProperty(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
