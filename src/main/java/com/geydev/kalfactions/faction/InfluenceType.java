package com.geydev.kalfactions.faction;

import java.util.Locale;
import java.util.Optional;

public enum InfluenceType {
    SCIENCE,
    ECONOMIC,
    MILITARY;

    public static final InfluenceType[] VALUES = values();

    public int index() {
        return ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "kingdoms.influence." + id();
    }

    public static Optional<InfluenceType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
