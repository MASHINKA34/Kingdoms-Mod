package com.geydev.kalfactions.outpost.cluster.distribution;

public record ZoneMultipliers(
        double density,
        double reserve,
        double size
) {
    public ZoneMultipliers {
        requireMultiplier(density, "density");
        requireMultiplier(reserve, "reserve");
        requireMultiplier(size, "size");
    }

    public double densityProbability(double baseProbability) {
        if (!Double.isFinite(baseProbability) || baseProbability < 0.0D || baseProbability > 1.0D) {
            throw new IllegalArgumentException("baseProbability must be finite and between 0 and 1");
        }
        return Math.min(1.0D, baseProbability * density);
    }

    public int scaleReserve(int baseReserve, int upperBound) {
        return scale(baseReserve, reserve, upperBound);
    }

    public int scaleSize(int baseSize, int upperBound) {
        return scale(baseSize, size, upperBound);
    }

    private static int scale(int baseValue, double multiplier, int upperBound) {
        if (baseValue < 0) {
            throw new IllegalArgumentException("baseValue must not be negative");
        }
        if (upperBound < 0) {
            throw new IllegalArgumentException("upperBound must not be negative");
        }
        double scaled = baseValue * multiplier;
        if (scaled >= upperBound) {
            return upperBound;
        }
        return (int) Math.round(scaled);
    }

    private static void requireMultiplier(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " multiplier must be finite and non-negative");
        }
    }
}
