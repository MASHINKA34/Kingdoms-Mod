package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.List;
import java.util.Locale;

public enum FactionBonus {
    WARRIORS,
    MINERS,
    FARMERS,
    CRAFTERS,
    HOOKAH,
    BUILDERS,
    TRADERS;

    public static final List<FactionBonus> SELECTABLE = List.of(MINERS, FARMERS, BUILDERS, WARRIORS, HOOKAH);

    public double damageMultiplier() {
        return this == WARRIORS ? ModConfigSpec.WARRIOR_DAMAGE_MULTIPLIER.getAsDouble() : 1.0D;
    }

    public double oreBonusChance() {
        return this == MINERS ? ModConfigSpec.ORE_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double harvestBonusChance() {
        return this == FARMERS ? ModConfigSpec.HARVEST_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double craftBonusChance() {
        return this == CRAFTERS ? ModConfigSpec.CRAFT_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double claimDiscount() {
        return this == BUILDERS ? ModConfigSpec.BUILDER_DISCOUNT.getAsDouble() : 0.0D;
    }

    public String translationKey() {
        return "kingdoms.bonus." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
