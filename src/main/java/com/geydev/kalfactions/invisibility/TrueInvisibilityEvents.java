package com.geydev.kalfactions.invisibility;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class TrueInvisibilityEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!event.isCanceled()) {
            TrueInvisibility.breakFor(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) {
            TrueInvisibility.breakFor(event.getPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Player player) {
            TrueInvisibility.breakFor(player);
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (TrueInvisibility.isTrueInvisibility(event.getEffectInstance().getEffect())) {
            TrueInvisibility.broadcast(event.getEntity(), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        if (!event.isCanceled() && TrueInvisibility.isTrueInvisibility(event.getEffect())) {
            TrueInvisibility.broadcast(event.getEntity(), false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.isCanceled() || event.getEffectInstance() == null) {
            return;
        }
        if (TrueInvisibility.isTrueInvisibility(event.getEffectInstance().getEffect())) {
            TrueInvisibility.broadcast(event.getEntity(), false);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (event.getEntity() instanceof ServerPlayer viewer
                && target instanceof LivingEntity living
                && TrueInvisibility.isActive(living)) {
            TrueInvisibility.syncTo(viewer, target, true);
        }
    }

    private TrueInvisibilityEvents() {
    }
}
