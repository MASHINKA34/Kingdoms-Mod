package com.geydev.kalfactions.invisibility;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.InvisibilityChaliceBlockEntity;
import com.geydev.kalfactions.client.ClientTrueInvisibility;
import com.geydev.kalfactions.client.screen.InvisibilityChaliceSettingsScreen;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
public final class InvisibilityNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final double MAX_EDIT_DISTANCE_SQUARED = 64.0D;
    private static final int ACTION_COOLDOWN_TICKS = 5;
    private static final Map<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(
                InvisibilityPayloads.S2COpenChaliceSettings.TYPE,
                InvisibilityPayloads.S2COpenChaliceSettings.STREAM_CODEC,
                InvisibilityNetwork::handleOpen
        );
        registrar.playToServer(
                InvisibilityPayloads.C2SUpdateChaliceSettings.TYPE,
                InvisibilityPayloads.C2SUpdateChaliceSettings.STREAM_CODEC,
                InvisibilityNetwork::handleUpdate
        );
        registrar.playToClient(
                InvisibilityPayloads.S2CTrueInvisibility.TYPE,
                InvisibilityPayloads.S2CTrueInvisibility.STREAM_CODEC,
                InvisibilityNetwork::handleTrueInvisibility
        );
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ACTION_TICK.remove(event.getEntity().getUUID());
    }

    public static void openSettings(ServerPlayer player, BlockPos pos) {
        InvisibilityChaliceBlockEntity chalice = editableChalice(player, pos);
        if (chalice == null) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new InvisibilityPayloads.S2COpenChaliceSettings(pos, chalice.durationSeconds())
        );
    }

    public static boolean applySettings(Player player, BlockPos pos, int durationSeconds) {
        if (!TrueInvisibility.isValidSeconds(durationSeconds)) {
            return false;
        }
        InvisibilityChaliceBlockEntity chalice = editableChalice(player, pos);
        if (chalice == null || rateLimited(player)) {
            return false;
        }
        chalice.configure(durationSeconds);
        return true;
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    private static void handleUpdate(
            InvisibilityPayloads.C2SUpdateChaliceSettings payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = payload.pos().immutable();
        if (!applySettings(player, pos, payload.durationSeconds())) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.kingdoms.invisibility_chalice.settings_saved"),
                true
        );
    }

    private static void handleOpen(InvisibilityPayloads.S2COpenChaliceSettings payload, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            InvisibilityChaliceSettingsScreen.handleOpen(payload);
        }
    }

    private static void handleTrueInvisibility(
            InvisibilityPayloads.S2CTrueInvisibility payload,
            IPayloadContext context
    ) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientTrueInvisibility.handle(payload);
        }
    }

    @Nullable
    private static InvisibilityChaliceBlockEntity editableChalice(Player player, BlockPos pos) {
        if (!player.hasPermissions(2)
                || !player.isAlive()
                || !(player.level() instanceof ServerLevel level)
                || player.distanceToSqr(pos.getCenter()) > MAX_EDIT_DISTANCE_SQUARED
                || !level.isLoaded(pos)
                || !level.getBlockState(pos).is(ModBlocks.INVISIBILITY_CHALICE.get())) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof InvisibilityChaliceBlockEntity chalice) {
            return chalice;
        }
        return null;
    }

    private static boolean rateLimited(Player player) {
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        return previous != null && now - previous < ACTION_COOLDOWN_TICKS;
    }

    private InvisibilityNetwork() {
    }
}
