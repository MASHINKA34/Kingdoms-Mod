package com.geydev.kalfactions.outpost.trader;

import java.util.Locale;
import java.util.Optional;

public enum SellerTraderRole {
    PERMANENT,
    CONTRABAND,
    WANDERING;

    private static final SellerTraderRole[] VALUES = values();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SellerTraderRole byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? PERMANENT : VALUES[ordinal];
    }

    public static Optional<SellerTraderRole> parse(String value) {
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
