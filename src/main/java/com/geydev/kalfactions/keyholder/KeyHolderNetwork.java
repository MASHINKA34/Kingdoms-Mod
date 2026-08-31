package com.geydev.kalfactions.keyholder;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.KeyHolderBlock;
import com.geydev.kalfactions.block.KeyHolderBlockEntity;
import com.geydev.kalfactions.block.KeyHolderMode;
import com.geydev.kalfactions.client.screen.KeyHolderSettingsScreen;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
public final class KeyHolderNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final double MAX_EDIT_DISTANCE_SQUARED = 64.0D;
    private static final int ACTION_COOLDOWN_TICKS = 5;
    private static final Map<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(
                KeyHolderPayloads.C2SUpdateSettings.TYPE,
                KeyHolderPayloads.C2SUpdateSettings.STREAM_CODEC,
                KeyHolderNetwork::handleUpdate
        );
        registrar.playToClient(
                KeyHolderPayloads.S2COpenSettings.TYPE,
                KeyHolderPayloads.S2COpenSettings.STREAM_CODEC,
                KeyHolderNetwork::handleOpen
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ACTION_TICK.remove(event.getEntity().getUUID());
    }

    public static void openSettings(ServerPlayer player, BlockPos pos) {
        KeyHolderBlockEntity holder = editableHolder(player, pos);
        if (holder == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new KeyHolderPayloads.S2COpenSettings(
                pos,
                holder.mode().getSerializedName(),
                holder.pulseTicks(),
                holder.consumeKey()
        ));
    }

    public static boolean applySettings(
            Player player,
            BlockPos pos,
            String mode,
            int pulseTicks,
            boolean consumeKey
    ) {
        if (!isValidSettings(mode, pulseTicks)) {
            return false;
        }
        KeyHolderBlockEntity holder = editableHolder(player, pos);
        if (holder == null || rateLimited(player)) {
            return false;
        }
        holder.configure(KeyHolderMode.fromSerializedName(mode), pulseTicks, consumeKey);
        resetActiveSignal((ServerLevel) player.level(), pos);
        return true;
    }

    public static boolean isValidSettings(String mode, int pulseTicks) {
        return mode != null
                && mode.length() <= KeyHolderPayloads.MODE_NAME_LENGTH
                && KeyHolderMode.isValidName(mode)
                && KeyHolderBlockEntity.isValidPulseTicks(pulseTicks);
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    private static void handleUpdate(KeyHolderPayloads.C2SUpdateSettings payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = payload.pos().immutable();
        if (!applySettings(player, pos, payload.mode(), payload.pulseTicks(), payload.consumeKey())) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.kingdoms.key_holder.settings_saved"),
                true
        );
    }

    private static void resetActiveSignal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getValue(KeyHolderBlock.POWERED)) {
            return;
        }
        Block block = state.getBlock();
        level.setBlock(pos, state.setValue(KeyHolderBlock.POWERED, Boolean.FALSE), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, block);
        level.updateNeighbourForOutputSignal(pos, block);
    }

    private static KeyHolderBlockEntity editableHolder(Player player, BlockPos pos) {
        if (!player.hasPermissions(2)
                || !player.isAlive()
                || !(player.level() instanceof ServerLevel level)
                || player.distanceToSqr(pos.getCenter()) > MAX_EDIT_DISTANCE_SQUARED
                || !level.isLoaded(pos)
                || !level.getBlockState(pos).is(ModBlocks.KEY_HOLDER.get())) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof KeyHolderBlockEntity holder) {
            return holder;
        }
        return null;
    }

    private static boolean rateLimited(Player player) {
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        return previous != null && now - previous < ACTION_COOLDOWN_TICKS;
    }

    private static void handleOpen(KeyHolderPayloads.S2COpenSettings payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            KeyHolderSettingsScreen.handleOpen(payload);
        }
    }

    private KeyHolderNetwork() {
    }
}
