package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class DungeonSight {
    public static final int MIN_PERCENT = 10;
    public static final int MAX_PERCENT = 100;
    public static final int MAX_AMPLIFIER = 9;

    private static final int REFRESH_BELOW_TICKS = DungeonPresenceEvents.CHECK_INTERVAL_TICKS * 3;

    public static int amplifierFor(int percent) {
        return Math.clamp(Math.clamp(percent, MIN_PERCENT, MAX_PERCENT) / 10 - 1, 0, MAX_AMPLIFIER);
    }

    public static float scaleFor(int amplifier) {
        return (Math.clamp(amplifier, 0, MAX_AMPLIFIER) + 1) / 10.0F;
    }

    public static String lightingKey(int lighting) {
        return switch (Math.clamp(lighting, 0, DungeonManager.MAX_LIGHTING)) {
            case 1 -> "screen.kingdoms.dungeon.lighting.dim";
            case 2 -> "screen.kingdoms.dungeon.lighting.medium";
            case 3 -> "screen.kingdoms.dungeon.lighting.full";
            default -> "screen.kingdoms.dungeon.lighting.off";
        };
    }

    public static void refresh(ServerPlayer player, DungeonManager.DungeonView dungeon) {
        int lighting = dungeon == null ? 0 : dungeon.lighting();
        MobEffectInstance existing = player.getEffect(ModEffects.DUNGEON_SIGHT);
        if (lighting <= 0 && existing == null) {
            return;
        }
        DungeonSightRules rules = DungeonSightRules.configured();
        if (!rules.enabled() || lighting <= 0) {
            if (existing != null) {
                player.removeEffect(ModEffects.DUNGEON_SIGHT);
            }
            return;
        }
        int amplifier = amplifierFor(rules.percentFor(lighting));
        if (existing != null) {
            if (existing.getAmplifier() == amplifier && existing.getDuration() >= REFRESH_BELOW_TICKS) {
                return;
            }
            if (existing.getAmplifier() != amplifier) {
                player.removeEffect(ModEffects.DUNGEON_SIGHT);
            }
        }
        player.addEffect(new MobEffectInstance(
                ModEffects.DUNGEON_SIGHT,
                rules.effectTicks(),
                amplifier,
                true,
                false,
                false
        ));
    }

    private DungeonSight() {
    }
}
