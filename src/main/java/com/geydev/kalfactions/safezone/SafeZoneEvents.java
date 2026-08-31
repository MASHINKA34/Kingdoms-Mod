package com.geydev.kalfactions.safezone;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.item.SafeZoneWandItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SafeZoneEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim && isProtected(victim)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof Player
                && event.getSource().getEntity() instanceof ServerPlayer attacker
                && isProtected(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getItemStack().getItem() instanceof SafeZoneWandItem)
                || !player.isShiftKeyDown()
                || !player.hasPermissions(2)) {
            return;
        }
        SafeZoneService.removeAt(player, event.getPos());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafeZoneService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafeZoneService.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SafeZoneService.syncTo(player);
        }
    }

    public static boolean isProtected(ServerPlayer player) {
        return SafeZoneManager.get(player.serverLevel())
                .isProtected(player.level().dimension(), player.position());
    }

    private SafeZoneEvents() {
    }
}
