package com.geydev.kalfactions.outpost.cluster.distribution;

public enum ResourceZone {
    BLUE(0x243F6A8885A308D3L, new ZoneMultipliers(0.45D, 0.60D, 0.65D)),
    YELLOW(0x13198A2E03707344L, new ZoneMultipliers(1.00D, 1.00D, 1.00D)),
    BLACK(0xA4093822299F31D0L, new ZoneMultipliers(1.65D, 1.50D, 1.45D));

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
            double yellowRadius
    ) {
        requireFinite(x, "x");
        requireFinite(z, "z");
        requireFinite(spawnX, "spawnX");
        requireFinite(spawnZ, "spawnZ");
        double distance = Math.hypot(x - spawnX, z - spawnZ);
        return fromDistance(distance, blueRadius, yellowRadius);
    }

    public static ResourceZone fromDistance(double distance, double blueRadius, double yellowRadius) {
        validateRadii(blueRadius, yellowRadius);
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        if (distance <= blueRadius) {
            return BLUE;
        }
        if (distance <= yellowRadius) {
            return YELLOW;
        }
        return BLACK;
    }

    static void validateRadii(double blueRadius, double yellowRadius) {
        if (!Double.isFinite(blueRadius) || blueRadius < 0.0D) {
            throw new IllegalArgumentException("blueRadius must be finite and non-negative");
        }
        if (!Double.isFinite(yellowRadius) || yellowRadius < blueRadius) {
            throw new IllegalArgumentException("yellowRadius must be finite and at least blueRadius");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
