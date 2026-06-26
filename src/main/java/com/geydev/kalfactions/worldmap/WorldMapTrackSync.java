package com.geydev.kalfactions.worldmap;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class WorldMapTrackSync {
    private static final int BROADCAST_INTERVAL_TICKS = 600;
    private static int ticks;

    private WorldMapTrackSync() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < BROADCAST_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        broadcast(server);
    }

    public static void broadcast(MinecraftServer server) {
        WorldMapStorage.Meta meta = WorldMapStorage.meta(server).orElse(null);
        if (meta == null) {
            return;
        }
        FactionPayloads.S2CWorldMapTracks payload = build(meta);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void send(ServerPlayer player, WorldMapStorage.Meta meta) {
        PacketDistributor.sendToPlayer(player, build(meta));
    }

    private static FactionPayloads.S2CWorldMapTracks build(WorldMapStorage.Meta meta) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, meta.dimension());
        float[] segments = WorldMapTracks.collect(dimension, meta.centerX(), meta.centerZ(), meta.regionBlocks());
        return new FactionPayloads.S2CWorldMapTracks(meta.dimension(), segments);
    }
}
