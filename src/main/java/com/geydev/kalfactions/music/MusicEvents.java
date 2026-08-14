package com.geydev.kalfactions.music;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class MusicEvents {
    private static int tickCounter;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        MusicService.pumpDownloads(server);
        if (++tickCounter < MusicLimits.RADIUS_TICK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        MusicService.expireSessions();
        MusicRadius.refreshAll(server);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MusicRadius.forget(player.getUUID());
            MusicRadius.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MusicService.forget(event.getEntity().getUUID());
        MusicRadius.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MusicRadius.stopFor(player);
            MusicRadius.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MusicRadius.stopFor(player);
            MusicRadius.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(ModBlocks.MUSIC_BLOCK.get())) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!MusicService.canPlace(player, event.getPos())) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("kingdoms.music.error.no_place_permission"));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MusicService.clear();
        MusicRadius.clear();
        tickCounter = 0;
    }

    private MusicEvents() {
    }
}
