package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class BlackZoneTicker {
    private static final int INTERVAL_TICKS = 20;

    private static int ticksUntilCheck;
    private static boolean suspended;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilCheck > 0) {
            return;
        }
        ticksUntilCheck = INTERVAL_TICKS;
        if (!BlackZoneService.enabled()) {
            if (!suspended) {
                suspended = true;
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    BlackZoneService.suspend(player);
                }
            }
            return;
        }
        suspended = false;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive()) {
                continue;
            }
            if (BlackZoneService.exempt(player)) {
                BlackZoneService.suspend(player);
                continue;
            }
            BlackZoneService.tick(player.serverLevel(), player, BlackZoneService.inBlackZone(player));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        BlackZoneService.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BlackZoneService.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                && level.getServer() != null) {
            BlackZoneService.reset(level.getServer(), player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            BlackZoneService.reset(player.getServer(), player);
        }
    }

    private BlackZoneTicker() {
    }
}
