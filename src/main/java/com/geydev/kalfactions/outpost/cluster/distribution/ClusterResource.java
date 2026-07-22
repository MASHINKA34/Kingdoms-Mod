package com.geydev.kalfactions.outpost.cluster.distribution;

import java.util.Locale;
import java.util.Optional;

public enum ClusterResource {
    COAL(30, 24, 18, false),
    COPPER(24, 20, 16, false),
    ZINC(14, 14, 13, false),
    IRON(22, 20, 18, false),
    LAPIS(4, 7, 10, false),
    REDSTONE(3, 7, 10, false),
    GOLD(2, 5, 9, true),
    DIAMOND(1, 3, 6, true);

    private final int blueWeight;
    private final int yellowWeight;
    private final int blackWeight;
    private final boolean rare;

    ClusterResource(int blueWeight, int yellowWeight, int blackWeight, boolean rare) {
        this.blueWeight = blueWeight;
        this.yellowWeight = yellowWeight;
        this.blackWeight = blackWeight;
        this.rare = rare;
    }

    public int weight(ResourceZone zone) {
        return switch (zone) {
            case BLUE -> blueWeight;
            case YELLOW -> yellowWeight;
            case BLACK -> blackWeight;
        };
    }

    public boolean isRare() {
        return rare;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ClusterResource> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static ClusterResource selectWeighted(ResourceZone zone, double unitRoll) {
        if (zone == null) {
            throw new NullPointerException("zone");
        }
        if (!Double.isFinite(unitRoll) || unitRoll < 0.0D || unitRoll >= 1.0D) {
            throw new IllegalArgumentException("unitRoll must be finite and between 0 inclusive and 1 exclusive");
        }
        int totalWeight = 0;
        for (ClusterResource resource : values()) {
            totalWeight += resource.weight(zone);
        }
        double selectedWeight = unitRoll * totalWeight;
        int cumulativeWeight = 0;
        for (ClusterResource resource : values()) {
            cumulativeWeight += resource.weight(zone);
            if (selectedWeight < cumulativeWeight) {
                return resource;
            }
        }
        return DIAMOND;
    }
}
