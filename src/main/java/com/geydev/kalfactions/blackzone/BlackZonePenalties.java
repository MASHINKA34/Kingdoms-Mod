package com.geydev.kalfactions.blackzone;

import com.geydev.kalfactions.KalFactions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class BlackZonePenalties {
    public static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "black_zone_toll");

    private static final Map<UUID, Map<Holder<MobEffect>, Integer>> APPLIED = new HashMap<>();

    public static void apply(Player player, long accumulatedMillis, boolean inZone) {
        Map<Holder<MobEffect>, Integer> desired = BlackZoneStage.effectsAt(accumulatedMillis, inZone);
        Map<Holder<MobEffect>, Integer> applied = adopt(player.getUUID(), accumulatedMillis);

        for (Holder<MobEffect> effect : List.copyOf(applied.keySet())) {
            Integer wanted = desired.get(effect);
            if (wanted == null) {
                player.removeEffect(effect);
                applied.remove(effect);
            } else if (wanted < applied.get(effect)) {
                player.removeEffect(effect);
            }
        }

        for (Map.Entry<Holder<MobEffect>, Integer> entry : desired.entrySet()) {
            refresh(player, entry.getKey(), entry.getValue());
            applied.put(entry.getKey(), entry.getValue());
        }

        applyHealthPenalty(player, BlackZoneStage.healthPenaltyAt(accumulatedMillis));
    }

    public static void clear(Player player) {
        Map<Holder<MobEffect>, Integer> applied = APPLIED.remove(player.getUUID());
        if (applied != null) {
            for (Holder<MobEffect> effect : applied.keySet()) {
                player.removeEffect(effect);
            }
        }
        for (Holder<MobEffect> effect : managedEffects()) {
            MobEffectInstance existing = player.getEffect(effect);
            if (existing != null && existing.isInfiniteDuration()) {
                player.removeEffect(effect);
            }
        }
        applyHealthPenalty(player, 0);
    }

    public static void forget(UUID playerId) {
        APPLIED.remove(playerId);
    }

    public static boolean tracked(UUID playerId) {
        return APPLIED.containsKey(playerId);
    }

    public static void adoptFor(UUID playerId, long accumulatedMillis) {
        adopt(playerId, accumulatedMillis);
    }

    public static int healthPenaltyOn(Player player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier modifier = attribute == null ? null : attribute.getModifier(HEALTH_MODIFIER_ID);
        return modifier == null ? 0 : (int) Math.round(-modifier.amount());
    }

    private static Map<Holder<MobEffect>, Integer> adopt(UUID playerId, long accumulatedMillis) {
        Map<Holder<MobEffect>, Integer> applied = APPLIED.get(playerId);
        if (applied != null) {
            return applied;
        }
        applied = new LinkedHashMap<>(BlackZoneStage.effectsAt(accumulatedMillis, true));
        APPLIED.put(playerId, applied);
        return applied;
    }

    private static void refresh(Player player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance existing = player.getEffect(effect);
        if (existing != null && existing.getAmplifier() >= amplifier && existing.isInfiniteDuration()) {
            return;
        }
        player.addEffect(new MobEffectInstance(
                effect,
                MobEffectInstance.INFINITE_DURATION,
                amplifier,
                false,
                false,
                true
        ));
    }

    private static void applyHealthPenalty(Player player, int penalty) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double capped = Math.min(Math.max(0, penalty), Math.max(0.0D, attribute.getBaseValue() - 1.0D));
        AttributeModifier existing = attribute.getModifier(HEALTH_MODIFIER_ID);
        if (capped <= 0.0D) {
            if (existing != null) {
                attribute.removeModifier(HEALTH_MODIFIER_ID);
            }
            return;
        }
        if (existing != null && existing.amount() == -capped) {
            clampHealth(player);
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(
                HEALTH_MODIFIER_ID,
                -capped,
                AttributeModifier.Operation.ADD_VALUE
        ));
        clampHealth(player);
    }

    private static void clampHealth(Player player) {
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static List<Holder<MobEffect>> managedEffects() {
        List<Holder<MobEffect>> effects = new ArrayList<>();
        for (BlackZoneStage stage : BlackZoneStage.values()) {
            if (stage.effect() != null && !effects.contains(stage.effect())) {
                effects.add(stage.effect());
            }
        }
        return effects;
    }

    private BlackZonePenalties() {
    }
}
