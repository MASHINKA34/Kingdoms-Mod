package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Periodically advances faction influence. {@link FactionManager#tickInfluence(long)}
 * awards influence per claimed chunk per elapsed game-day, so polling a few times a
 * second is cheap (it is a no-op until a day boundary is crossed).
 */
@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InfluenceTicker {
    private static final int INTERVAL_TICKS = 100;
    private static int ticksSinceLastUpdate;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticksSinceLastUpdate < INTERVAL_TICKS) {
            return;
        }
        ticksSinceLastUpdate = 0;
        MinecraftServer server = event.getServer();
        FactionManager.get(server).tickInfluence(server.overworld().getGameTime());
    }

    private InfluenceTicker() {
    }
}
