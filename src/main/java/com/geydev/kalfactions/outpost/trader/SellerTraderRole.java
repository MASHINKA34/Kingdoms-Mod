package com.geydev.kalfactions.outpost.trader;

import java.util.Locale;
import java.util.Optional;

public enum SellerTraderRole {
    PERMANENT,
    CONTRABAND,
    WANDERING;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
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
