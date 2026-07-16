package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientNewsState {
    private static volatile int unreadNews;

    public static void accept(int count) {
        unreadNews = Math.max(0, count);
    }

    public static int unreadNews() {
        return unreadNews;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        unreadNews = 0;
    }

    private ClientNewsState() {
    }
}
