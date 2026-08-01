package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.DimensionControlScreen;
import com.geydev.kalfactions.client.screen.NetherStatusScreen;
import com.geydev.kalfactions.faction.FactionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DimensionNetwork {
    private static final String PROTOCOL_VERSION = "5";
    private static final Map<UUID, Long> LAST_STATUS_REQUEST = new HashMap<>();

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                DimensionPayloads.C2SDimensionAction.TYPE,
                DimensionPayloads.C2SDimensionAction.STREAM_CODEC,
                DimensionNetwork::handleAction
        );
        registrar.playToServer(
                DimensionPayloads.C2SNetherStatusRequest.TYPE,
                DimensionPayloads.C2SNetherStatusRequest.STREAM_CODEC,
                DimensionNetwork::handleStatusRequest
        );
        registrar.playToClient(
                DimensionPayloads.S2CDimensionState.TYPE,
                DimensionPayloads.S2CDimensionState.STREAM_CODEC,
                DimensionNetwork::handleState
        );
        registrar.playToClient(
                DimensionPayloads.S2CNetherStatus.TYPE,
                DimensionPayloads.S2CNetherStatus.STREAM_CODEC,
                DimensionNetwork::handleNetherStatus
        );
    }

    private static void handleAction(DimensionPayloads.C2SDimensionAction payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        UUID nextInteractionId = DimensionKeySessions.accept(
                player.getUUID(), payload.interactionId(), server.getTickCount()
        ).orElse(null);
        if (nextInteractionId == null) {
            return;
        }
        DimensionControlManager control = DimensionControlManager.get(server);
        ResourceKey<Level> dimension = payload.end() ? Level.END : Level.NETHER;
        Component name = dimensionName(payload.end());
        Component notice;
        boolean successful = true;
        switch (payload.action()) {
            case DimensionPayloads.ACTION_OPEN -> {
                if (control.setClosed(dimension, false)) {
                    DimensionControlEvents.broadcastOpened(server, dimension);
                    notice = Component.translatable("kingdoms.dimension.notice.opened", name);
                } else {
                    notice = Component.translatable("kingdoms.dimension.notice.no_change");
                    successful = false;
                }
            }
            case DimensionPayloads.ACTION_CLOSE -> {
                if (control.setClosed(dimension, true)) {
                    int moved = DimensionControlEvents.evacuateForClosure(server, dimension);
                    notice = Component.translatable("kingdoms.dimension.notice.closed", name, moved);
                } else {
                    notice = Component.translatable("kingdoms.dimension.notice.no_change");
                    successful = false;
                }
            }
            case DimensionPayloads.ACTION_WIPE_SCHEDULE -> {
                boolean changed = payload.end()
                        ? control.setWipePending(Level.END, true)
                        : control.requestNetherWipeFromDimensionKey();
                if (changed) {
                    notice = Component.translatable("kingdoms.dimension.notice.wipe_scheduled", name);
                } else {
                    notice = Component.translatable("kingdoms.dimension.notice.no_change");
                    successful = false;
                }
            }
            case DimensionPayloads.ACTION_WIPE_CANCEL -> {
                boolean changed = payload.end()
                        ? control.setWipePending(Level.END, false)
                        : control.cancelNetherWipeFromDimensionKey();
                if (changed) {
                    notice = Component.translatable("kingdoms.dimension.notice.wipe_cancelled", name);
                } else {
                    notice = Component.translatable("kingdoms.dimension.notice.no_change");
                    successful = false;
                }
            }
            default -> {
                return;
            }
        }
        sendState(player, nextInteractionId, notice, successful);
    }

    public static void openControl(ServerPlayer player) {
        UUID interactionId = DimensionKeySessions.open(
                player.getUUID(), player.serverLevel().getServer().getTickCount()
        );
        sendState(player, interactionId, Component.empty(), true);
    }

    private static void sendState(
            ServerPlayer player,
            UUID interactionId,
            Component notice,
            boolean successful
    ) {
        MinecraftServer server = player.serverLevel().getServer();
        DimensionControlManager control = DimensionControlManager.get(server);
        Instant now = Instant.now();
        PacketDistributor.sendToPlayer(player, new DimensionPayloads.S2CDimensionState(
                interactionId,
                control.isClosed(Level.NETHER),
                control.isWipePending(Level.NETHER),
                playersIn(server, Level.NETHER),
                control.isNetherOpenForPlayers(now),
                NetherSchedulePolicy.secondsUntilClose(now),
                control.activeSessions(now).size(),
                control.netherPortal().isPresent(),
                control.isClosed(Level.END),
                control.isWipePending(Level.END),
                playersIn(server, Level.END),
                notice,
                successful
        ));
    }

    public static void removePlayer(UUID playerId) {
        DimensionKeySessions.remove(playerId);
        LAST_STATUS_REQUEST.remove(playerId);
    }

    public static void clear() {
        DimensionKeySessions.clear();
        LAST_STATUS_REQUEST.clear();
    }

    private static int playersIn(MinecraftServer server, ResourceKey<Level> dimension) {
        ServerLevel level = server.getLevel(dimension);
        return level == null ? 0 : level.players().size();
    }

    private static Component dimensionName(boolean end) {
        return Component.translatable(end ? "kingdoms.dimension.name.end" : "kingdoms.dimension.name.nether");
    }

    private static void handleState(DimensionPayloads.S2CDimensionState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DimensionControlScreen.handle(payload);
        }
    }

    private static void handleStatusRequest(
            DimensionPayloads.C2SNetherStatusRequest payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
        long tick = server.getTickCount();
        Long previous = LAST_STATUS_REQUEST.put(player.getUUID(), tick);
        if (previous != null && tick - previous < 10L) {
            return;
        }
        sendNetherStatus(player, Instant.now());
    }

    static DimensionPayloads.S2CNetherStatus statusPayload(ServerPlayer player, Instant now) {
        MinecraftServer server = player.serverLevel().getServer();
        DimensionControlManager control = DimensionControlManager.get(server);
        UUID factionId = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
        boolean scheduleOpen = NetherSchedulePolicy.isOpen(now);
        boolean open = control.isNetherOpenForPlayers(now);
        Instant phaseEnd = open
                ? NetherSchedulePolicy.closeInstant(now)
                : NetherSchedulePolicy.nextOpenInstant(now);
        int remaining = factionId == null ? -1 : control.remainingSessions(factionId, now);
        long sessionEnd = control.assignedSession(player.getUUID(), now)
                .filter(session -> factionId != null && session.factionId().equals(factionId))
                .map(session -> session.endsAt().toEpochMilli())
                .orElse(0L);
        return new DimensionPayloads.S2CNetherStatus(
                now.toEpochMilli(),
                phaseEnd.toEpochMilli(),
                open,
                scheduleOpen && control.isClosed(Level.NETHER),
                remaining,
                control.rules().sessionsPerDay(),
                sessionEnd
        );
    }

    private static void sendNetherStatus(ServerPlayer player, Instant now) {
        PacketDistributor.sendToPlayer(player, statusPayload(player, now));
    }

    private static void handleNetherStatus(DimensionPayloads.S2CNetherStatus payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NetherStatusScreen.handle(payload);
        }
    }

    private DimensionNetwork() {
    }
}
