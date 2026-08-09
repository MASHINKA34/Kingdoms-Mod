package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientScoutState;
import com.geydev.kalfactions.client.screen.ScoutOrderScreen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ScoutNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                ScoutPayloads.C2SScoutOrder.TYPE,
                ScoutPayloads.C2SScoutOrder.STREAM_CODEC,
                ScoutNetwork::handleOrder
        );
        registrar.playToServer(
                ScoutPayloads.C2SRequestScoutState.TYPE,
                ScoutPayloads.C2SRequestScoutState.STREAM_CODEC,
                ScoutNetwork::handleRequestState
        );
        registrar.playToClient(
                ScoutPayloads.S2COpenScout.TYPE,
                ScoutPayloads.S2COpenScout.STREAM_CODEC,
                ScoutNetwork::handleOpen
        );
        registrar.playToClient(
                ScoutPayloads.S2CScoutState.TYPE,
                ScoutPayloads.S2CScoutState.STREAM_CODEC,
                ScoutNetwork::handleState
        );
    }

    private static void handleOrder(ScoutPayloads.C2SScoutOrder payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ScoutService.placeOrder(player, payload);
        }
    }

    private static void handleRequestState(ScoutPayloads.C2SRequestScoutState payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ScoutService.pushState(player);
        }
    }

    private static void handleOpen(ScoutPayloads.S2COpenScout payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ScoutOrderScreen.handle(payload);
        }
    }

    private static void handleState(ScoutPayloads.S2CScoutState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientScoutState.accept(payload);
        }
    }

    private ScoutNetwork() {
    }
}
