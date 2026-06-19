package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SellerOfferTicker {
    private static final int INTERVAL_TICKS = 20;
    private static int ticksSinceLastCheck;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticksSinceLastCheck < INTERVAL_TICKS) {
            return;
        }
        ticksSinceLastCheck = 0;
        SellerOfferRotation.get(event.getServer()).refreshIfNeeded(event.getServer(), System.currentTimeMillis());
    }

    private SellerOfferTicker() {
    }
}
