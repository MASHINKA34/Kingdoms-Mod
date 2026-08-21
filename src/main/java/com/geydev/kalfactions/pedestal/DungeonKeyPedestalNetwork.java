package com.geydev.kalfactions.pedestal;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonKeyPedestalActivation;
import com.geydev.kalfactions.block.DungeonKeyPedestalBlock;
import com.geydev.kalfactions.block.DungeonKeyPedestalBlockEntity;
import com.geydev.kalfactions.client.screen.DungeonKeyPedestalSettingsScreen;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonKeyPedestalNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final double MAX_EDIT_DISTANCE_SQUARED = 64.0D;

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                DungeonKeyPedestalPayloads.C2SUpdateSettings.TYPE,
                DungeonKeyPedestalPayloads.C2SUpdateSettings.STREAM_CODEC,
                DungeonKeyPedestalNetwork::handleUpdate
        );
        registrar.playToClient(
                DungeonKeyPedestalPayloads.S2COpenSettings.TYPE,
                DungeonKeyPedestalPayloads.S2COpenSettings.STREAM_CODEC,
                DungeonKeyPedestalNetwork::handleOpen
        );
    }

    public static void openSettings(ServerPlayer player, BlockPos pos) {
        DungeonKeyPedestalBlockEntity pedestal = editablePedestal(player, pos);
        if (pedestal == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new DungeonKeyPedestalPayloads.S2COpenSettings(
                pos,
                pedestal.requiredKey().getSerializedName(),
                pedestal.signalTicks()
        ));
    }

    private static void handleUpdate(
            DungeonKeyPedestalPayloads.C2SUpdateSettings payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        DungeonKeyPedestalBlockEntity pedestal = editablePedestal(player, payload.pos());
        if (pedestal == null) {
            return;
        }
        DungeonKeyPedestalActivation requiredKey =
                DungeonKeyPedestalActivation.fromSerializedName(payload.requiredKey());
        pedestal.configure(requiredKey, payload.signalTicks());
        resetActiveSignal(player, payload.pos());
        player.displayClientMessage(
                Component.translatable("message.kingdoms.dungeon_key_pedestal.settings_saved"),
                true
        );
    }

    private static void resetActiveSignal(ServerPlayer player, BlockPos pos) {
        BlockState state = player.serverLevel().getBlockState(pos);
        if (state.getValue(DungeonKeyPedestalBlock.ACTIVATION) == DungeonKeyPedestalActivation.NONE) {
            return;
        }
        Block block = state.getBlock();
        player.serverLevel().setBlock(
                pos,
                state.setValue(DungeonKeyPedestalBlock.ACTIVATION, DungeonKeyPedestalActivation.NONE),
                Block.UPDATE_ALL
        );
        player.serverLevel().updateNeighborsAt(pos, block);
        player.serverLevel().updateNeighbourForOutputSignal(pos, block);
    }

    private static DungeonKeyPedestalBlockEntity editablePedestal(ServerPlayer player, BlockPos pos) {
        if (!player.hasPermissions(2)
                || !player.isAlive()
                || player.distanceToSqr(pos.getCenter()) > MAX_EDIT_DISTANCE_SQUARED
                || !player.serverLevel().isLoaded(pos)
                || !player.serverLevel().getBlockState(pos).is(ModBlocks.DUNGEON_KEY_PEDESTAL.get())) {
            return null;
        }
        if (player.serverLevel().getBlockEntity(pos) instanceof DungeonKeyPedestalBlockEntity pedestal) {
            return pedestal;
        }
        return null;
    }

    private static void handleOpen(
            DungeonKeyPedestalPayloads.S2COpenSettings payload,
            IPayloadContext context
    ) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            DungeonKeyPedestalSettingsScreen.handleOpen(payload);
        }
    }

    private DungeonKeyPedestalNetwork() {
    }
}
