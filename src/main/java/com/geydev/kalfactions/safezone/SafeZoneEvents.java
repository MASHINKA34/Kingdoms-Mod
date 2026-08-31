package com.geydev.kalfactions.safezone;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

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

    public static boolean isProtected(ServerPlayer player) {
        return SafeZoneManager.get(player.serverLevel())
                .isProtected(player.level().dimension(), player.position());
    }

    private SafeZoneEvents() {
    }
}
