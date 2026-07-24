package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientQuarryState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class QuarryNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                QuarryPayloads.C2SRequestState.TYPE,
                QuarryPayloads.C2SRequestState.STREAM_CODEC,
                QuarryNetwork::handleRequest
        );
        registrar.playToServer(
                QuarryPayloads.C2SAction.TYPE,
                QuarryPayloads.C2SAction.STREAM_CODEC,
                QuarryNetwork::handleAction
        );
        registrar.playToClient(
                QuarryPayloads.S2CState.TYPE,
                QuarryPayloads.S2CState.STREAM_CODEC,
                QuarryNetwork::handleState
        );
    }

    private static void handleRequest(QuarryPayloads.C2SRequestState payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuarryService.requestState(player, payload);
        }
    }

    private static void handleAction(QuarryPayloads.C2SAction payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            QuarryService.performAction(player, payload);
        }
    }

    private static void handleState(QuarryPayloads.S2CState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientQuarryState.accept(payload);
        }
    }

    private QuarryNetwork() {
    }
}
