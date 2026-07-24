package com.geydev.kalfactions.outpost.cluster.distribution;

public enum ResourceZone {
    BLUE(0x243F6A8885A308D3L, new ZoneMultipliers(0.00D, 0.00D, 0.00D)),
    YELLOW(0x13198A2E03707344L, new ZoneMultipliers(0.50D, 0.50D, 0.50D)),
    RED(0xA4093822299F31D0L, new ZoneMultipliers(1.10D, 1.10D, 1.10D)),
    BLACK(0x082EFA98EC4E6C89L, new ZoneMultipliers(1.70D, 1.70D, 1.70D));

    private final long salt;
    private final ZoneMultipliers defaultMultipliers;

    ResourceZone(long salt, ZoneMultipliers defaultMultipliers) {
        this.salt = salt;
        this.defaultMultipliers = defaultMultipliers;
    }

    public long salt() {
        return salt;
    }

    public ZoneMultipliers defaultMultipliers() {
        return defaultMultipliers;
    }

    public static ResourceZone at(
            double x,
            double z,
            double spawnX,
            double spawnZ,
            double blueRadius,
            double yellowRadius,
            double redRadius
    ) {
        requireFinite(x, "x");
        requireFinite(z, "z");
        requireFinite(spawnX, "spawnX");
        requireFinite(spawnZ, "spawnZ");
        double distance = Math.max(Math.abs(x - spawnX), Math.abs(z - spawnZ));
        return fromDistance(distance, blueRadius, yellowRadius, redRadius);
    }

    public static ResourceZone fromDistance(
            double distance,
            double blueRadius,
            double yellowRadius,
            double redRadius
    ) {
        validateRadii(blueRadius, yellowRadius, redRadius);
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        if (distance <= blueRadius) {
            return BLUE;
        }
        if (distance <= yellowRadius) {
            return YELLOW;
        }
        if (distance <= redRadius) {
            return RED;
        }
        return BLACK;
    }

    static void validateRadii(double blueRadius, double yellowRadius, double redRadius) {
        if (!Double.isFinite(blueRadius) || blueRadius < 0.0D) {
            throw new IllegalArgumentException("blueRadius must be finite and non-negative");
        }
        if (!Double.isFinite(yellowRadius) || yellowRadius < blueRadius) {
            throw new IllegalArgumentException("yellowRadius must be finite and at least blueRadius");
        }
        if (!Double.isFinite(redRadius) || redRadius < yellowRadius) {
            throw new IllegalArgumentException("redRadius must be finite and at least yellowRadius");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
