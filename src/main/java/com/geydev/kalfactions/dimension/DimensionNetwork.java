package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.screen.DimensionControlScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.time.Instant;
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
    private static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                DimensionPayloads.C2SDimensionAction.TYPE,
                DimensionPayloads.C2SDimensionAction.STREAM_CODEC,
                DimensionNetwork::handleAction
        );
        registrar.playToClient(
                DimensionPayloads.S2CDimensionState.TYPE,
                DimensionPayloads.S2CDimensionState.STREAM_CODEC,
                DimensionNetwork::handleState
        );
    }

    private static void handleAction(DimensionPayloads.C2SDimensionAction payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) {
            return;
        }
        MinecraftServer server = player.serverLevel().getServer();
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
                    int moved = DimensionControlEvents.evacuate(server, dimension);
                    notice = Component.translatable("kingdoms.dimension.notice.closed", name, moved);
                } else {
                    notice = Component.translatable("kingdoms.dimension.notice.no_change");
                    successful = false;
                }
            }
            case DimensionPayloads.ACTION_WIPE_SCHEDULE -> {
                if (control.setWipePending(dimension, true)) {
                    DimensionControlEvents.evacuate(server, dimension);
                    notice = Component.translatable("kingdoms.dimension.notice.wipe_scheduled", name);
                } else {
                    notice = Component.translatable("kingdoms.dimension.notice.no_change");
                    successful = false;
                }
            }
            case DimensionPayloads.ACTION_WIPE_CANCEL -> {
                if (control.setWipePending(dimension, false)) {
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
        sendState(player, notice, successful);
    }

    public static void sendState(ServerPlayer player, Component notice, boolean successful) {
        MinecraftServer server = player.serverLevel().getServer();
        DimensionControlManager control = DimensionControlManager.get(server);
        Instant now = Instant.now();
        PacketDistributor.sendToPlayer(player, new DimensionPayloads.S2CDimensionState(
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

    private DimensionNetwork() {
    }
}
