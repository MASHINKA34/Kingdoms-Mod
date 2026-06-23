package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.List;
import java.util.Locale;

public enum FactionBonus {
    MINERS,
    FARMERS,
    BUILDERS,
    ASSASSINS,
    HOOKAH,
    ENCHANTERS,
    MERCHANTS,
    NOMADS,
    RESEARCHERS;

    public static final List<FactionBonus> SELECTABLE = List.of(
            MINERS,
            FARMERS,
            BUILDERS,
            ASSASSINS,
            HOOKAH,
            ENCHANTERS,
            MERCHANTS,
            NOMADS,
            RESEARCHERS
    );

    public double oreBonusChance() {
        return this == MINERS ? ModConfigSpec.ORE_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double harvestBonusChance() {
        return this == FARMERS ? ModConfigSpec.HARVEST_BONUS_CHANCE.getAsDouble() : 0.0D;
    }

    public double claimDiscount() {
        return this == BUILDERS ? ModConfigSpec.BUILDER_DISCOUNT.getAsDouble() : 0.0D;
    }

    public static FactionBonus parse(String name) {
        String normalized = name == null ? "" : name.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WARRIORS" -> ASSASSINS;
            case "CRAFTERS" -> ENCHANTERS;
            case "TRADERS" -> MERCHANTS;
            default -> FactionBonus.valueOf(normalized);
        };
    }

    public String translationKey() {
        return "kingdoms.bonus." + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
