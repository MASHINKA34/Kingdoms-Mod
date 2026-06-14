package com.geydev.kalfactions.raid;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class RaidTicker {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        RaidManager.get(event.getServer()).tick(event.getServer(), System.currentTimeMillis());
    }

    private RaidTicker() {
    }
}
