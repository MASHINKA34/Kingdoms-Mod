package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientFactionPayloadHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FactionNetwork {
    private static final String PROTOCOL_VERSION = "1";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                FactionPayloads.C2SOpenTable.TYPE,
                FactionPayloads.C2SOpenTable.STREAM_CODEC,
                FactionNetwork::handleOpen
        );
        registrar.playToServer(
                FactionPayloads.C2SCreateFaction.TYPE,
                FactionPayloads.C2SCreateFaction.STREAM_CODEC,
                FactionNetwork::handleCreate
        );
        registrar.playToServer(
                FactionPayloads.C2SUpdateFaction.TYPE,
                FactionPayloads.C2SUpdateFaction.STREAM_CODEC,
                FactionNetwork::handleUpdate
        );
        registrar.playToServer(
                FactionPayloads.C2SSetClaim.TYPE,
                FactionPayloads.C2SSetClaim.STREAM_CODEC,
                FactionNetwork::handleClaim
        );
        registrar.playToClient(
                FactionPayloads.S2CFactionState.TYPE,
                FactionPayloads.S2CFactionState.STREAM_CODEC,
                FactionNetwork::handleState
        );
    }

    private static void handleOpen(FactionPayloads.C2SOpenTable payload, IPayloadContext context) {
        FactionServerHooks.openFor(serverPlayer(context), payload.tablePos());
    }

    private static void handleCreate(FactionPayloads.C2SCreateFaction payload, IPayloadContext context) {
        FactionServerHooks.create(serverPlayer(context), payload.tablePos(), payload.name(), payload.color());
    }

    private static void handleUpdate(FactionPayloads.C2SUpdateFaction payload, IPayloadContext context) {
        FactionServerHooks.update(serverPlayer(context), payload.tablePos(), payload.name(), payload.color());
    }

    private static void handleClaim(FactionPayloads.C2SSetClaim payload, IPayloadContext context) {
        FactionServerHooks.setClaim(
                serverPlayer(context),
                payload.tablePos(),
                payload.chunkX(),
                payload.chunkZ(),
                payload.claimed()
        );
    }

    private static void handleState(FactionPayloads.S2CFactionState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientFactionPayloadHandler.handle(payload);
        }
    }

    private static ServerPlayer serverPlayer(IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            return player;
        }
        throw new IllegalStateException("Received a faction C2S payload without a server player");
    }

    private FactionNetwork() {
    }
}
