package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;

public enum FactionBonus {
    WARRIORS,
    MINERS,
    CRAFTERS,
    HOOKAH,
    BUILDERS,
    TRADERS;

    public double damageMultiplier() {
        return this == WARRIORS ? ModConfigSpec.WARRIOR_DAMAGE_MULTIPLIER.getAsDouble() : 1.0D;
    }

    public double oreBonusChance() {
        return this == MINERS ? ModConfigSpec.ORE_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double craftBonusChance() {
        return this == CRAFTERS ? ModConfigSpec.CRAFT_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double claimDiscount() {
        return this == BUILDERS ? ModConfigSpec.BUILDER_DISCOUNT.getAsDouble() : 0.0D;
    }
}
