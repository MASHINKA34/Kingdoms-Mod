package com.geydev.kalfactions.safezone;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientSafeZoneStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SafeZoneNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                SafeZonePayloads.S2CSyncSafeZones.TYPE,
                SafeZonePayloads.S2CSyncSafeZones.STREAM_CODEC,
                SafeZoneNetwork::handleSync
        );
        registrar.playToServer(
                SafeZonePayloads.C2SAdjustSelection.TYPE,
                SafeZonePayloads.C2SAdjustSelection.STREAM_CODEC,
                SafeZoneNetwork::handleAdjustSelection
        );
    }

    private static void handleSync(SafeZonePayloads.S2CSyncSafeZones payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSafeZoneStore.handle(payload);
        }
    }

    private static void handleAdjustSelection(
            SafeZonePayloads.C2SAdjustSelection payload,
            IPayloadContext context
    ) {
        if (context.player() instanceof ServerPlayer player) {
            SafeZoneService.adjustSelection(player, payload.face(), payload.delta());
        }
    }

    private SafeZoneNetwork() {
    }
}
