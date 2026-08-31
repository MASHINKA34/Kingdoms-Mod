package com.geydev.kalfactions.invisibility;

import com.geydev.kalfactions.registry.ModEffects;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TrueInvisibility {
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 3600;
    public static final int DEFAULT_SECONDS = 60;

    public static int clampSeconds(int seconds) {
        return Math.clamp(seconds, MIN_SECONDS, MAX_SECONDS);
    }

    public static boolean isValidSeconds(int seconds) {
        return seconds >= MIN_SECONDS && seconds <= MAX_SECONDS;
    }

    public static void grant(LivingEntity entity, int seconds) {
        int duration = clampSeconds(seconds) * 20;
        entity.addEffect(instance(ModEffects.TRUE_INVISIBILITY, duration));
        entity.addEffect(instance(MobEffects.INVISIBILITY, duration));
    }

    public static boolean isActive(LivingEntity entity) {
        return entity.hasEffect(ModEffects.TRUE_INVISIBILITY);
    }

    public static boolean revoke(LivingEntity entity) {
        if (!isActive(entity)) {
            return false;
        }
        entity.removeEffect(ModEffects.TRUE_INVISIBILITY);
        entity.removeEffect(MobEffects.INVISIBILITY);
        return true;
    }

    public static boolean breakFor(@Nullable Player player) {
        if (player == null || player.level().isClientSide() || !revoke(player)) {
            return false;
        }
        player.displayClientMessage(Component.translatable("message.kingdoms.true_invisibility.broken"), true);
        return true;
    }

    public static boolean isTrueInvisibility(@Nullable Holder<MobEffect> effect) {
        return effect != null && effect.value() == ModEffects.TRUE_INVISIBILITY.get();
    }

    public static void broadcast(Entity entity, boolean active) {
        if (entity.level().isClientSide()) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                entity,
                new InvisibilityPayloads.S2CTrueInvisibility(entity.getId(), active)
        );
    }

    public static void syncTo(ServerPlayer viewer, Entity entity, boolean active) {
        PacketDistributor.sendToPlayer(
                viewer,
                new InvisibilityPayloads.S2CTrueInvisibility(entity.getId(), active)
        );
    }

    private static MobEffectInstance instance(Holder<MobEffect> effect, int duration) {
        return new MobEffectInstance(effect, duration, 0, false, false, true);
    }

    private TrueInvisibility() {
    }
}
