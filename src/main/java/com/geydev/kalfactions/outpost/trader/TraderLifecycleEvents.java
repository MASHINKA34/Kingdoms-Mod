package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class TraderLifecycleEvents {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 0) {
            TraderLifecycle.tick(event.getServer(), System.currentTimeMillis());
        }
    }

    private TraderLifecycleEvents() {
    }
}
