package com.geydev.kalfactions.quarry;

public final class QuarryDistribution {
    private static final long X_SALT = 0x9E3779B97F4A7C15L;
    private static final long Z_SALT = 0xC2B2AE3D27D4EB4FL;

    public static boolean isCandidate(long worldSeed, int chunkX, int chunkZ, int minimumSpacingChunks) {
        int spacing = Math.max(1, minimumSpacingChunks);
        long own = score(worldSeed, chunkX, chunkZ);
        for (int dx = -spacing; dx <= spacing; dx++) {
            for (int dz = -spacing; dz <= spacing; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                long neighbor = score(worldSeed, chunkX + dx, chunkZ + dz);
                int comparison = Long.compareUnsigned(neighbor, own);
                if (comparison < 0
                        || comparison == 0
                        && (dx < 0 || dx == 0 && dz < 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static long score(long seed, int chunkX, int chunkZ) {
        return mix64(seed ^ chunkX * X_SALT ^ chunkZ * Z_SALT);
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private QuarryDistribution() {
    }
}
