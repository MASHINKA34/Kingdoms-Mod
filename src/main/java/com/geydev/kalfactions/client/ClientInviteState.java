package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientInviteState {
    private static volatile int pendingInvites;

    public static void accept(int count) {
        pendingInvites = Math.max(0, count);
    }

    public static int pendingInvites() {
        return pendingInvites;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingInvites = 0;
    }

    private ClientInviteState() {
    }
}
