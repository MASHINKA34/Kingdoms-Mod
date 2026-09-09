package com.geydev.kalfactions.charon;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class CharonNetwork {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(com.geydev.kalfactions.net.KingdomsProtocol.VERSION);
        registrar.playToClient(
                CharonPayloads.S2CGhostState.TYPE,
                CharonPayloads.S2CGhostState.STREAM_CODEC,
                CharonNetwork::handleGhostState
        );
    }

    public static void broadcast(ServerPlayer player, boolean active) {
        CharonPayloads.S2CGhostState payload = new CharonPayloads.S2CGhostState(player.getUUID(), active);
        PacketDistributor.sendToPlayer(player, payload);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
    }

    public static void syncTo(ServerPlayer viewer, ServerPlayer ghost) {
        PacketDistributor.sendToPlayer(viewer, new CharonPayloads.S2CGhostState(ghost.getUUID(), true));
    }

    private static void handleGhostState(CharonPayloads.S2CGhostState payload, IPayloadContext context) {
        CharonGhosts.set(payload.playerId(), payload.active());
    }

    private CharonNetwork() {
    }
}
