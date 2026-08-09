package com.geydev.kalfactions.client;

import com.geydev.kalfactions.ClientBridge;
import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.scout.ScoutPayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientScoutState {
    private static volatile ScoutPayloads.S2CScoutState state = ScoutPayloads.S2CScoutState.idle();

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    public static void accept(ScoutPayloads.S2CScoutState payload) {
        state = payload == null ? ScoutPayloads.S2CScoutState.idle() : payload;
        ClientBridge.setScoutBusy(state.active());
    }

    public static void reset() {
        state = ScoutPayloads.S2CScoutState.idle();
        ClientBridge.setScoutBusy(false);
    }

    public static ScoutPayloads.S2CScoutState state() {
        return state;
    }

    public static boolean active() {
        return state.active();
    }

    public static long remainingMillis() {
        return Math.max(0L, state.endsAtMillis() - System.currentTimeMillis());
    }

    private ClientScoutState() {
    }
}
