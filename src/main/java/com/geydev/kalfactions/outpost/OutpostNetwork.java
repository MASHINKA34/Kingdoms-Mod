package com.geydev.kalfactions.outpost;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.OutpostScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class OutpostNetwork {
    private static final String PROTOCOL_VERSION = "3";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                OutpostPayloads.C2SDismantleOutpost.TYPE,
                OutpostPayloads.C2SDismantleOutpost.STREAM_CODEC,
                OutpostNetwork::handleDismantle
        );
        registrar.playToClient(
                OutpostPayloads.S2COutpostState.TYPE,
                OutpostPayloads.S2COutpostState.STREAM_CODEC,
                OutpostNetwork::handleState
        );
    }

    private static void handleDismantle(OutpostPayloads.C2SDismantleOutpost payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            OutpostInteractions.dismantleFromUi(player, payload.core());
        }
    }

    private static void handleState(OutpostPayloads.S2COutpostState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            OutpostScreen.open(payload);
        }
    }

    private OutpostNetwork() {
    }
}
