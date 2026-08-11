package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class FaithBonuses {
    public static int activeLevel(ServerPlayer player, FaithGod god) {
        if (player == null || god == null || player.getServer() == null) {
            return 0;
        }
        UUID factionId = FactionManager.get(player.serverLevel())
                .getFactionIdForMember(player.getUUID())
                .orElse(null);
        if (factionId == null) {
            return 0;
        }
        FaithManager manager = FaithManager.get(player.serverLevel());
        long buffEnd = manager.buffEndMillis(factionId, god);
        if (buffEnd <= System.currentTimeMillis() || manager.hasForfeited(player.getUUID(), god, buffEnd)) {
            return 0;
        }
        return manager.level(factionId, god);
    }

    public static double scienceCraftChance(int level) {
        return level <= 0 ? 0.0D : Math.clamp(
                ModConfigSpec.FAITH_SCIENCE_CRAFT_CHANCE_PER_LEVEL.getAsDouble() * level, 0.0D, 1.0D);
    }

    public static double scienceExperienceBonus(int level) {
        int minLevel = ModConfigSpec.FAITH_SCIENCE_XP_MIN_LEVEL.getAsInt();
        if (level < minLevel) {
            return 0.0D;
        }
        return Math.max(0.0D, ModConfigSpec.FAITH_SCIENCE_XP_PER_LEVEL.getAsDouble() * (level - minLevel + 1));
    }

    public static double warBonusDamage(int level) {
        return level <= 0 ? 0.0D : Math.max(0.0D, ModConfigSpec.FAITH_WAR_DAMAGE_PER_LEVEL.getAsDouble() * level);
    }

    public static double warBonusHealth(int level) {
        int minLevel = ModConfigSpec.FAITH_WAR_HEALTH_MIN_LEVEL.getAsInt();
        if (level < minLevel) {
            return 0.0D;
        }
        return Math.max(0.0D, ModConfigSpec.FAITH_WAR_HEALTH_PER_LEVEL.getAsDouble() * (level - minLevel + 1));
    }

    public static double economySellPercent(int level) {
        return level <= 0
                ? 0.0D
                : Math.max(0.0D, ModConfigSpec.FAITH_ECONOMY_SELL_PERCENT_PER_LEVEL.getAsDouble() * level);
    }

    public static double economyDropChance(int level) {
        int minLevel = ModConfigSpec.FAITH_ECONOMY_DROP_MIN_LEVEL.getAsInt();
        if (level < minLevel) {
            return 0.0D;
        }
        return Math.clamp(
                ModConfigSpec.FAITH_ECONOMY_DROP_CHANCE_PER_LEVEL.getAsDouble() * (level - minLevel + 1),
                0.0D,
                1.0D
        );
    }

    public static boolean economyHighlight(int level) {
        return level > 0 && level >= ModConfigSpec.FAITH_ECONOMY_HIGHLIGHT_LEVEL.getAsInt();
    }

    private FaithBonuses() {
    }
}
