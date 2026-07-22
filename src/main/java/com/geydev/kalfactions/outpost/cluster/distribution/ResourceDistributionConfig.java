package com.geydev.kalfactions.outpost.cluster.distribution;

public record ResourceDistributionConfig(
        double blueRadius,
        double yellowRadius,
        int cellSize,
        double baseDensityProbability,
        int minBaseReserve,
        int maxBaseReserve,
        int maxReserve,
        int minBaseSize,
        int maxBaseSize,
        int maxSize,
        double rareSizeMultiplier
) {
    public static final double DEFAULT_BLUE_RADIUS = 5_000.0D;
    public static final double DEFAULT_YELLOW_RADIUS = 8_000.0D;
    public static final int DEFAULT_CELL_SIZE = 256;
    public static final double DEFAULT_BASE_DENSITY_PROBABILITY = 0.40D;
    public static final int DEFAULT_MIN_BASE_RESERVE = 100;
    public static final int DEFAULT_MAX_BASE_RESERVE = 200;
    public static final int DEFAULT_MAX_RESERVE = 300;
    public static final int DEFAULT_MIN_BASE_SIZE = 20;
    public static final int DEFAULT_MAX_BASE_SIZE = 80;
    public static final int DEFAULT_MAX_SIZE = 120;
    public static final double DEFAULT_RARE_SIZE_MULTIPLIER = 0.65D;

    public ResourceDistributionConfig {
        ResourceZone.validateRadii(blueRadius, yellowRadius);
        if (cellSize < 1 || cellSize > 4_096) {
            throw new IllegalArgumentException("cellSize must be between 1 and 4096");
        }
        if (!Double.isFinite(baseDensityProbability)
                || baseDensityProbability < 0.0D
                || baseDensityProbability > 1.0D) {
            throw new IllegalArgumentException("baseDensityProbability must be finite and between 0 and 1");
        }
        validateRange(minBaseReserve, maxBaseReserve, maxReserve, "reserve");
        validateRange(minBaseSize, maxBaseSize, maxSize, "size");
        if (!Double.isFinite(rareSizeMultiplier)
                || rareSizeMultiplier <= 0.0D
                || rareSizeMultiplier > 1.0D) {
            throw new IllegalArgumentException("rareSizeMultiplier must be finite and between 0 exclusive and 1 inclusive");
        }
    }

    public static ResourceDistributionConfig defaults() {
        return defaults(DEFAULT_BLUE_RADIUS, DEFAULT_YELLOW_RADIUS);
    }

    public static ResourceDistributionConfig defaults(double blueRadius, double yellowRadius) {
        return new ResourceDistributionConfig(
                blueRadius,
                yellowRadius,
                DEFAULT_CELL_SIZE,
                DEFAULT_BASE_DENSITY_PROBABILITY,
                DEFAULT_MIN_BASE_RESERVE,
                DEFAULT_MAX_BASE_RESERVE,
                DEFAULT_MAX_RESERVE,
                DEFAULT_MIN_BASE_SIZE,
                DEFAULT_MAX_BASE_SIZE,
                DEFAULT_MAX_SIZE,
                DEFAULT_RARE_SIZE_MULTIPLIER
        );
    }

    private static void validateRange(int minimum, int maximum, int upperBound, String name) {
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException(name + " base range is invalid");
        }
        if (upperBound < maximum) {
            throw new IllegalArgumentException(name + " upper bound must be at least the base maximum");
        }
    }
}
