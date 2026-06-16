package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ChunkLoaderTicker {
    private static final int INTERVAL_TICKS = 200;
    private static int ticks;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = event.getServer();
        FactionManager.get(server).reconcileForceLoads(server);
    }

    private ChunkLoaderTicker() {
    }
}
