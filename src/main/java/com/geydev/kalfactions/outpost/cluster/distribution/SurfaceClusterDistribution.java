package com.geydev.kalfactions.outpost.cluster.distribution;

import java.util.Optional;

public final class SurfaceClusterDistribution {
    private static final long X_SALT = 0x632BE59BD9B4E019L;
    private static final long Z_SALT = 0x8CB92BA72F3D8DD7L;
    private static final long POSITION_X_SALT = 0x94D049BB133111EBL;
    private static final long POSITION_Z_SALT = 0xBF58476D1CE4E5B9L;
    private static final long TYPE_SALT = 0xDB4F0B9175AE2165L;
    private static final long RICHNESS_SALT = 0xBBE0563303A4615FL;

    private final long worldSeed;
    private final int spawnX;
    private final int spawnZ;
    private final int blueRadius;
    private final int yellowRadius;
    private final int redRadius;
    private final int yellowSpacing;
    private final int redSpacing;
    private final int maximumSpacing;

    public SurfaceClusterDistribution(
            long worldSeed,
            int spawnX,
            int spawnZ,
            int blueRadius,
            int yellowRadius,
            int redRadius,
            int yellowSpacing,
            int redSpacing
    ) {
        ResourceZone.validateRadii(blueRadius, yellowRadius, redRadius);
        if (yellowSpacing < 1 || redSpacing < 1) {
            throw new IllegalArgumentException("surface cluster spacing must be positive");
        }
        this.worldSeed = worldSeed;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.blueRadius = blueRadius;
        this.yellowRadius = yellowRadius;
        this.redRadius = redRadius;
        this.yellowSpacing = yellowSpacing;
        this.redSpacing = redSpacing;
        this.maximumSpacing = Math.max(yellowSpacing, redSpacing);
    }

    public Optional<Candidate> candidateForChunk(int chunkX, int chunkZ) {
        ResourceZone zone = zoneAt(chunkX, chunkZ);
        int spacing = spacing(zone);
        if (spacing == 0) {
            return Optional.empty();
        }
        long priority = priority(chunkX, chunkZ);
        for (int dx = -maximumSpacing; dx <= maximumSpacing; dx++) {
            for (int dz = -maximumSpacing; dz <= maximumSpacing; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int neighborX = chunkX + dx;
                int neighborZ = chunkZ + dz;
                int neighborSpacing = spacing(zoneAt(neighborX, neighborZ));
                if (neighborSpacing == 0
                        || Math.max(Math.abs(dx), Math.abs(dz)) > Math.max(spacing, neighborSpacing)) {
                    continue;
                }
                long neighborPriority = priority(neighborX, neighborZ);
                if (Long.compareUnsigned(neighborPriority, priority) < 0
                        || neighborPriority == priority && precedes(neighborX, neighborZ, chunkX, chunkZ)) {
                    return Optional.empty();
                }
            }
        }
        long seed = priority ^ worldSeed;
        int localX = 2 + unsignedMod(mix64(seed ^ POSITION_X_SALT), 12);
        int localZ = 2 + unsignedMod(mix64(seed ^ POSITION_Z_SALT), 12);
        int typeRoll = unsignedMod(mix64(seed ^ TYPE_SALT), 100);
        int richness = 1 + unsignedMod(mix64(seed ^ RICHNESS_SALT), 3);
        return Optional.of(new Candidate(zone, localX, localZ, typeRoll, richness));
    }

    public ResourceZone zoneAt(int chunkX, int chunkZ) {
        return ResourceZone.at(
                (chunkX << 4) + 8,
                (chunkZ << 4) + 8,
                spawnX,
                spawnZ,
                blueRadius,
                yellowRadius,
                redRadius
        );
    }

    private int spacing(ResourceZone zone) {
        return switch (zone) {
            case YELLOW -> yellowSpacing;
            case RED -> redSpacing;
            case BLUE, BLACK -> 0;
        };
    }

    private long priority(int chunkX, int chunkZ) {
        return mix64(worldSeed ^ chunkX * X_SALT ^ chunkZ * Z_SALT);
    }

    private static boolean precedes(int firstX, int firstZ, int secondX, int secondZ) {
        return firstX < secondX || firstX == secondX && firstZ < secondZ;
    }

    private static int unsignedMod(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record Candidate(ResourceZone zone, int localX, int localZ, int typeRoll, int richness) {
    }
}
