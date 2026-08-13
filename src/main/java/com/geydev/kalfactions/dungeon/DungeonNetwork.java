package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientDungeonState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonNetwork {
    private static final String PROTOCOL_VERSION = "2";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                DungeonPayloads.C2SRenameDungeon.TYPE,
                DungeonPayloads.C2SRenameDungeon.STREAM_CODEC,
                DungeonNetwork::handleRename
        );
        registrar.playToServer(
                DungeonPayloads.C2SRemoveDungeon.TYPE,
                DungeonPayloads.C2SRemoveDungeon.STREAM_CODEC,
                DungeonNetwork::handleRemove
        );
        registrar.playToServer(
                DungeonPayloads.C2SDungeonSetClaim.TYPE,
                DungeonPayloads.C2SDungeonSetClaim.STREAM_CODEC,
                DungeonNetwork::handleSetClaim
        );
        registrar.playToServer(
                DungeonPayloads.C2SDungeonMapSet.TYPE,
                DungeonPayloads.C2SDungeonMapSet.STREAM_CODEC,
                DungeonNetwork::handleMapSet
        );
        registrar.playToServer(
                DungeonPayloads.C2SDungeonChestAction.TYPE,
                DungeonPayloads.C2SDungeonChestAction.STREAM_CODEC,
                DungeonNetwork::handleChestAction
        );
        registrar.playToClient(
                DungeonPayloads.S2COpenDungeon.TYPE,
                DungeonPayloads.S2COpenDungeon.STREAM_CODEC,
                DungeonNetwork::handleOpen
        );
        registrar.playToClient(
                DungeonPayloads.S2CDungeonChestState.TYPE,
                DungeonPayloads.S2CDungeonChestState.STREAM_CODEC,
                DungeonNetwork::handleChestState
        );
    }

    private static void handleChestAction(DungeonPayloads.C2SDungeonChestAction payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DungeonService.chestAction(player, payload);
        }
    }

    private static void handleChestState(DungeonPayloads.S2CDungeonChestState payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientDungeonState.acceptChest(payload);
        }
    }

    private static void handleRename(DungeonPayloads.C2SRenameDungeon payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DungeonService.rename(player, payload.dungeonId(), payload.name());
        }
    }

    private static void handleRemove(DungeonPayloads.C2SRemoveDungeon payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DungeonService.remove(player, payload.dungeonId());
        }
    }

    private static void handleSetClaim(DungeonPayloads.C2SDungeonSetClaim payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DungeonService.setClaim(
                    player,
                    payload.dungeonId(),
                    payload.corePos(),
                    payload.chunkX(),
                    payload.chunkZ(),
                    payload.marked()
            );
        }
    }

    private static void handleMapSet(DungeonPayloads.C2SDungeonMapSet payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DungeonService.mapSet(player, payload.dungeonId(), payload.marked(), payload.chunks());
        }
    }

    private static void handleOpen(DungeonPayloads.S2COpenDungeon payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientDungeonState.accept(payload);
        }
    }

    private DungeonNetwork() {
    }
}
