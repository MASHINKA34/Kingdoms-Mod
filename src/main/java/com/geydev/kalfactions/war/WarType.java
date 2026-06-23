package com.geydev.kalfactions.war;

import java.util.Locale;
import java.util.Optional;

/**
 * Flavour classification of a war (a casus belli), chosen by the attacker when declaring. War types
 * do not change any war rules; they exist purely for presentation and to be recorded in the war
 * history alongside the free-text reason.
 */
public enum WarType {
    CONQUEST,
    HOLY_WAR,
    LIBERATION,
    PLUNDER,
    BORDER_DISPUTE,
    VENGEANCE;

    public static final WarType DEFAULT = CONQUEST;

    /** Stable lowercase identifier used for NBT and network serialization. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key for the type's display name. */
    public String displayKey() {
        return "kingdoms.war.type." + id();
    }

    /** Translation key for the short description shown in the picker. */
    public String descriptionKey() {
        return "kingdoms.war.type." + id() + ".desc";
    }

    public static Optional<WarType> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(id.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static WarType fromIdOrDefault(String id) {
        return fromId(id).orElse(DEFAULT);
    }
}
