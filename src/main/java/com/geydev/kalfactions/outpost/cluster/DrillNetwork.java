package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientDrillTargets;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DrillNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                DrillPayloads.C2SSelectTarget.TYPE,
                DrillPayloads.C2SSelectTarget.STREAM_CODEC,
                DrillNetwork::handleSelect
        );
        registrar.playToClient(
                DrillPayloads.S2CTargets.TYPE,
                DrillPayloads.S2CTargets.STREAM_CODEC,
                DrillNetwork::handleTargets
        );
    }

    private static void handleSelect(DrillPayloads.C2SSelectTarget payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DrillService.selectTarget(player, payload);
        }
    }

    private static void handleTargets(DrillPayloads.S2CTargets payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientDrillTargets.accept(payload);
        }
    }

    private DrillNetwork() {
    }
}
