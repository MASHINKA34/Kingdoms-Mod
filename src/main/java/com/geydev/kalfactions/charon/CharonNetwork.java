package com.geydev.kalfactions.charon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientCharonStatueHandler;
import com.geydev.kalfactions.net.ActionCooldown;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class CharonNetwork {
    private static final int ACTION_COOLDOWN_TICKS = 5;
    private static final Map<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(com.geydev.kalfactions.net.KingdomsProtocol.VERSION);
        registrar.playToClient(
                CharonPayloads.S2CGhostState.TYPE,
                CharonPayloads.S2CGhostState.STREAM_CODEC,
                CharonNetwork::handleGhostState
        );
        registrar.playToClient(
                CharonPayloads.S2CStatueOffer.TYPE,
                CharonPayloads.S2CStatueOffer.STREAM_CODEC,
                CharonNetwork::handleStatueOffer
        );
        registrar.playToServer(
                CharonPayloads.C2SStatueBuy.TYPE,
                CharonPayloads.C2SStatueBuy.STREAM_CODEC,
                CharonNetwork::handleStatueBuy
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ACTION_TICK.remove(event.getEntity().getUUID());
    }

    public static void broadcast(ServerPlayer player, boolean active) {
        CharonPayloads.S2CGhostState payload = new CharonPayloads.S2CGhostState(player.getUUID(), active);
        PacketDistributor.sendToPlayer(player, payload);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);
    }

    public static void syncTo(ServerPlayer viewer, ServerPlayer ghost) {
        PacketDistributor.sendToPlayer(viewer, new CharonPayloads.S2CGhostState(ghost.getUUID(), true));
    }

    public static void sendStatueOffer(ServerPlayer player, BlockPos anchor, long price) {
        PacketDistributor.sendToPlayer(player, new CharonPayloads.S2CStatueOffer(anchor, price));
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    private static void handleGhostState(CharonPayloads.S2CGhostState payload, IPayloadContext context) {
        CharonGhosts.set(payload.playerId(), payload.active());
    }

    private static void handleStatueOffer(CharonPayloads.S2CStatueOffer payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientCharonStatueHandler.handleOffer(payload);
        }
    }

    private static void handleStatueBuy(CharonPayloads.C2SStatueBuy payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || rateLimited(player)) {
            return;
        }
        CharonStatueShop.buy(player, payload.anchor().immutable());
    }

    private static boolean rateLimited(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long previous = ActionCooldown.mark(
                LAST_ACTION_TICK, player.getUUID(), now, ACTION_COOLDOWN_TICKS);
        return previous != null && now - previous < ACTION_COOLDOWN_TICKS;
    }

    private CharonNetwork() {
    }
}
