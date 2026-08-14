package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.Locale;
import java.util.function.DoubleSupplier;

public enum LegacyEffect {
    ORE_DROP(FactionBonus.MINERS, "ore_drop", () -> ModConfigSpec.ORE_BONUS_CHANCE.getAsDouble(),
            0.15D, 0.18D, 0.20D, 0.22D, 0.25D),
    MINING_SPEED(FactionBonus.MINERS, "mining_speed", () -> ModConfigSpec.MINER_MINING_SPEED_BONUS.getAsDouble(),
            0.07D, 0.08D, 0.12D, 0.15D, 0.18D),
    HARVEST(FactionBonus.FARMERS, "harvest", () -> ModConfigSpec.HARVEST_BONUS_CHANCE.getAsDouble(),
            0.20D, 0.30D, 0.40D, 0.50D, 0.60D),
    TWINS(FactionBonus.FARMERS, "twins", () -> ModConfigSpec.FARMER_BREEDING_TWIN_CHANCE.getAsDouble(),
            0.30D, 0.40D, 0.50D, 0.60D, 0.70D),
    CLAIM_DISCOUNT(FactionBonus.BUILDERS, "claim_discount", () -> ModConfigSpec.BUILDER_DISCOUNT.getAsDouble(),
            0.25D, 0.30D, 0.35D, 0.40D, 0.45D),
    OUTPOST_SIZE(FactionBonus.BUILDERS, "outpost_size", () -> ModConfigSpec.BUILDER_OUTPOST_SIZE.getAsInt(),
            3.0D, 3.0D, 4.0D, 4.0D, 5.0D),
    BACK_DAMAGE(FactionBonus.ASSASSINS, "back_damage",
            () -> ModConfigSpec.ASSASSIN_BACK_DAMAGE_MULTIPLIER.getAsDouble() - 1.0D,
            0.30D, 0.35D, 0.37D, 0.40D, 0.55D),
    CRIT_DAMAGE(FactionBonus.ASSASSINS, "crit_damage", () -> 0.0D,
            0.0D, 0.0D, 0.05D, 0.10D, 0.15D),
    HOOKAH_ARMOR(FactionBonus.HOOKAH, "hookah_armor", () -> ModConfigSpec.HOOKAH_ARMOR_BONUS.getAsDouble(),
            3.0D, 4.0D, 6.0D, 7.0D, 8.0D),
    HOOKAH_SPEED(FactionBonus.HOOKAH, "hookah_speed", () -> ModConfigSpec.HOOKAH_SPEED_BONUS.getAsDouble(),
            0.10D, 0.13D, 0.15D, 0.18D, 0.20D),
    HOOKAH_DAMAGE(FactionBonus.HOOKAH, "hookah_damage",
            () -> ModConfigSpec.HOOKAH_DAMAGE_MULTIPLIER.getAsDouble() - 1.0D,
            0.20D, 0.25D, 0.30D, 0.35D, 0.40D),
    ANVIL_DISCOUNT(FactionBonus.ENCHANTERS, "anvil_discount", () -> 0.0D,
            0.20D, 0.30D, 0.40D, 0.50D, 0.60D),
    ANVIL_EXTRA_LEVEL(FactionBonus.ENCHANTERS, "anvil_extra_level", () -> 0.0D,
            0.0D, 0.0D, 0.10D, 0.15D, 0.30D),
    SELL_PRICE(FactionBonus.MERCHANTS, "sell_price", () -> ModConfigSpec.MERCHANT_SELL_BONUS_PERCENT.getAsDouble(),
            0.13D, 0.15D, 0.20D, 0.25D, 0.30D),
    TREASURY_INCOME(FactionBonus.MERCHANTS, "treasury_income",
            () -> ModConfigSpec.MERCHANT_TREASURY_INCOME_PERCENT.getAsDouble(),
            0.20D, 0.22D, 0.25D, 0.30D, 0.34D),
    MOUNT_SPEED(FactionBonus.NOMADS, "mount_speed", () -> ModConfigSpec.NOMAD_MOUNT_SPEED_BONUS.getAsDouble(),
            0.20D, 0.30D, 0.55D, 0.65D, 0.70D),
    RESEARCH_SPEED(FactionBonus.RESEARCHERS, "research_speed",
            () -> ModConfigSpec.RESEARCHER_RESEARCH_SPEED_BONUS.getAsDouble(),
            0.25D, 0.30D, 0.40D, 0.45D, 0.50D),
    RESEARCH_DISCOUNT(FactionBonus.RESEARCHERS, "research_discount", () -> 0.0D,
            0.0D, 0.0D, 0.10D, 0.15D, 0.30D);

    private final FactionBonus bonus;
    private final String key;
    private final DoubleSupplier base;
    private final double[] levels;

    LegacyEffect(FactionBonus bonus, String key, DoubleSupplier base, double... levels) {
        this.bonus = bonus;
        this.key = key;
        this.base = base;
        this.levels = levels;
    }

    public FactionBonus bonus() {
        return bonus;
    }

    public String key() {
        return key;
    }

    public String translationKey() {
        return "kingdoms.research.legacy.effect." + key.toLowerCase(Locale.ROOT);
    }

    public double value(int level) {
        int clamped = Math.clamp(level, 0, LegacyResearch.MAX_LEVEL);
        if (clamped == 0) {
            return Math.max(0.0D, base.getAsDouble());
        }
        return Math.max(0.0D, levels[Math.min(clamped, levels.length) - 1]);
    }

    public boolean unlockedAt(int level) {
        return value(level) > 0.0D;
    }
}
