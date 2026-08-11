package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientFaithHighlightState;
import com.geydev.kalfactions.client.screen.FaithOfferingScreen;
import com.geydev.kalfactions.client.screen.FaithQuestScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FaithNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                FaithPayloads.S2CFaithState.TYPE,
                FaithPayloads.S2CFaithState.STREAM_CODEC,
                FaithNetwork::handleFaithState
        );
        registrar.playToClient(
                FaithPayloads.S2COreHighlight.TYPE,
                FaithPayloads.S2COreHighlight.STREAM_CODEC,
                FaithNetwork::handleOreHighlight
        );
        registrar.playToServer(
                FaithPayloads.C2SFaithAction.TYPE,
                FaithPayloads.C2SFaithAction.STREAM_CODEC,
                FaithNetwork::handleFaithAction
        );
    }

    private static void handleFaithState(FaithPayloads.S2CFaithState payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        if (payload.greatStatue()) {
            FaithQuestScreen.handle(payload);
        } else {
            FaithOfferingScreen.handle(payload);
        }
    }

    private static void handleOreHighlight(FaithPayloads.S2COreHighlight payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFaithHighlightState.accept(
                    payload.enabled(), payload.radius(), payload.maxBlocks(), payload.scanTicks());
        }
    }

    private static void handleFaithAction(FaithPayloads.C2SFaithAction payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FaithService.handleAction(player, payload.statuePos(), payload.action());
        }
    }

    private FaithNetwork() {
    }
}
