package com.geydev.kalfactions.item;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class WarpChannelEvents {
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    public enum Reason {
        RELEASED,
        MOVED,
        DAMAGED,
        INVALID
    }

    public static void begin(ServerPlayer player, InteractionHand hand) {
        WarpScrollRules rules = WarpScrollRules.configured();
        CHANNELS.put(player.getUUID(), new Channel(player.position(), hand, player.level().getGameTime(), 0));
        announce(player, rules.castTicks());
    }

    public static boolean isChanneling(ServerPlayer player) {
        return CHANNELS.containsKey(player.getUUID());
    }

    public static boolean complete(ServerPlayer player) {
        Channel channel = CHANNELS.remove(player.getUUID());
        if (channel == null) {
            return false;
        }
        WarpScrollRules rules = WarpScrollRules.configured();
        return !rules.cancelOnMove()
                || player.position().distanceToSqr(channel.start()) <= rules.moveToleranceSquared();
    }

    public static void cancel(ServerPlayer player, Reason reason) {
        Channel channel = CHANNELS.remove(player.getUUID());
        if (channel == null || reason == Reason.INVALID) {
            return;
        }
        int cooldown = cooldownTicks(WarpScrollRules.configured(), reason);
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(ModItems.WARP_SCROLL.get(), cooldown);
        }
        if (reason == Reason.RELEASED) {
            return;
        }
        player.stopUsingItem();
        player.displayClientMessage(interruption(reason, cooldown), true);
    }

    public static int cooldownTicks(WarpScrollRules rules, Reason reason) {
        return switch (reason) {
            case DAMAGED -> rules.damageCooldownTicks();
            case MOVED, RELEASED -> rules.cancelCooldownTicks();
            case INVALID -> 0;
        };
    }

    public static void tick(ServerPlayer player) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel == null) {
            return;
        }
        if (!player.isUsingItem() || !player.getItemInHand(channel.hand()).is(ModItems.WARP_SCROLL.get())) {
            cancel(player, Reason.RELEASED);
            return;
        }
        WarpScrollRules rules = WarpScrollRules.configured();
        if (rules.cancelOnMove()
                && player.position().distanceToSqr(channel.start()) > rules.moveToleranceSquared()) {
            cancel(player, Reason.MOVED);
            return;
        }
        long elapsed = player.level().getGameTime() - channel.startedGameTime();
        announce(player, rules.castTicks() - (int) Math.clamp(elapsed, 0L, rules.castTicks()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getNewDamage() <= 0.0F
                || !isChanneling(player)) {
            return;
        }
        if (WarpScrollRules.configured().cancelOnDamage()) {
            cancel(player, Reason.DAMAGED);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            cancel(player, Reason.INVALID);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CHANNELS.remove(event.getEntity().getUUID());
    }

    private static void announce(ServerPlayer player, int remainingTicks) {
        Channel channel = CHANNELS.get(player.getUUID());
        if (channel == null) {
            return;
        }
        int second = (Math.max(0, remainingTicks) + 19) / 20;
        if (second <= 0 || second == channel.lastAnnouncedSecond()) {
            return;
        }
        CHANNELS.put(player.getUUID(), channel.announced(second));
        player.displayClientMessage(
                Component.translatable("message.kingdoms.warp_scroll.casting", second),
                true
        );
    }

    private static Component interruption(Reason reason, int cooldownTicks) {
        if (reason == Reason.MOVED) {
            return Component.translatable("message.kingdoms.warp_scroll.interrupted_move");
        }
        if (cooldownTicks > 0) {
            return Component.translatable(
                    "message.kingdoms.warp_scroll.interrupted_damage",
                    (cooldownTicks + 19) / 20
            );
        }
        return Component.translatable("message.kingdoms.warp_scroll.interrupted");
    }

    private record Channel(Vec3 start, InteractionHand hand, long startedGameTime, int lastAnnouncedSecond) {
        private Channel announced(int second) {
            return new Channel(start, hand, startedGameTime, second);
        }
    }

    private WarpChannelEvents() {
    }
}
