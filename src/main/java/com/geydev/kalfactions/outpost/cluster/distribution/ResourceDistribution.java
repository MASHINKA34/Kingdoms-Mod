package com.geydev.kalfactions.outpost.cluster.distribution;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ResourceDistribution {
    private static final long CELL_X_SALT = 0x632BE59BD9B4E019L;
    private static final long CELL_Z_SALT = 0x8CB92BA72F3D8DD7L;
    private static final long CYCLE_SALT = 0x9E3779B97F4A7C15L;
    private static final long DENSITY_SALT = 0xD1B54A32D192ED03L;
    private static final long POSITION_X_SALT = 0x94D049BB133111EBL;
    private static final long POSITION_Z_SALT = 0xBF58476D1CE4E5B9L;
    private static final long RESOURCE_SALT = 0xDB4F0B9175AE2165L;
    private static final long RESERVE_SALT = 0xBBE0563303A4615FL;
    private static final long SIZE_SALT = 0xA0F2EC75A1FE1575L;
    private static final long SHAPE_SALT = 0x89E182857D9ED689L;
    private static final int POSITION_ATTEMPTS = 64;

    private final long worldSeed;
    private final long cycleId;
    private final int spawnX;
    private final int spawnZ;
    private final ResourceDistributionConfig config;
    private final Map<ResourceZone, ZoneMultipliers> zoneMultipliers;

    public ResourceDistribution(
            long worldSeed,
            long cycleId,
            int spawnX,
            int spawnZ,
            ResourceDistributionConfig config
    ) {
        this(worldSeed, cycleId, spawnX, spawnZ, config, defaultZoneMultipliers());
    }

    public ResourceDistribution(
            long worldSeed,
            long cycleId,
            int spawnX,
            int spawnZ,
            ResourceDistributionConfig config,
            Map<ResourceZone, ZoneMultipliers> zoneMultipliers
    ) {
        if (cycleId < 0L) {
            throw new IllegalArgumentException("cycleId must not be negative");
        }
        this.worldSeed = worldSeed;
        this.cycleId = cycleId;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.config = Objects.requireNonNull(config, "config");
        this.zoneMultipliers = copyProfiles(zoneMultipliers);
    }

    public static Map<ResourceZone, ZoneMultipliers> defaultZoneMultipliers() {
        EnumMap<ResourceZone, ZoneMultipliers> profiles = new EnumMap<>(ResourceZone.class);
        for (ResourceZone zone : ResourceZone.values()) {
            profiles.put(zone, zone.defaultMultipliers());
        }
        return Map.copyOf(profiles);
    }

    public long worldSeed() {
        return worldSeed;
    }

    public long cycleId() {
        return cycleId;
    }

    public ResourceDistributionConfig config() {
        return config;
    }

    public ResourceDistribution withCycleId(long nextCycleId) {
        return new ResourceDistribution(worldSeed, nextCycleId, spawnX, spawnZ, config, zoneMultipliers);
    }

    public ResourceZone zoneAt(double blockX, double blockZ) {
        return ResourceZone.at(
                blockX,
                blockZ,
                spawnX,
                spawnZ,
                config.blueRadius(),
                config.yellowRadius()
        );
    }

    public double densityProbability(ResourceZone zone) {
        return profile(zone).densityProbability(config.baseDensityProbability());
    }

    public Optional<CellCandidate> candidateForCell(int cellX, int cellZ) {
        CellBounds bounds = bounds(cellX, cellZ);
        if (bounds == null) {
            return Optional.empty();
        }
        ResourceZone zone = zoneAt(bounds.centerX(), bounds.centerZ());
        long cellSeed = cellSeed(cellX, cellZ, zone);
        double densityRoll = unitDouble(mix64(cellSeed ^ DENSITY_SALT));
        if (densityRoll >= densityProbability(zone)) {
            return Optional.empty();
        }
        Position position = positionInZone(bounds, zone, cellSeed);
        if (position == null) {
            return Optional.empty();
        }
        ClusterResource resource = ClusterResource.selectWeighted(
                zone,
                unitDouble(mix64(cellSeed ^ RESOURCE_SALT))
        );
        ZoneMultipliers profile = profile(zone);
        int reserve = profile.scaleReserve(
                rangedInt(cellSeed ^ RESERVE_SALT, config.minBaseReserve(), config.maxBaseReserve()),
                config.maxReserve()
        );
        int size = profile.scaleSize(
                rangedInt(cellSeed ^ SIZE_SALT, config.minBaseSize(), config.maxBaseSize()),
                config.maxSize()
        );
        return Optional.of(new CellCandidate(
                cellX,
                cellZ,
                position.blockX(),
                position.blockZ(),
                zone,
                resource,
                reserve,
                size,
                mix64(cellSeed ^ SHAPE_SALT)
        ));
    }

    private Position positionInZone(CellBounds bounds, ResourceZone zone, long cellSeed) {
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            long attemptSeed = mix64(cellSeed + CYCLE_SALT * (attempt + 1L));
            int offsetX = unsignedMod(mix64(attemptSeed ^ POSITION_X_SALT), config.cellSize());
            int offsetZ = unsignedMod(mix64(attemptSeed ^ POSITION_Z_SALT), config.cellSize());
            int blockX = (int) (bounds.minX() + offsetX);
            int blockZ = (int) (bounds.minZ() + offsetZ);
            if (zoneAt(blockX, blockZ) == zone) {
                return new Position(blockX, blockZ);
            }
        }
        return null;
    }

    private long cellSeed(int cellX, int cellZ, ResourceZone zone) {
        return mix64(
                worldSeed
                        ^ mix64(cycleId + CYCLE_SALT)
                        ^ zone.salt()
                        ^ (cellX * CELL_X_SALT)
                        ^ (cellZ * CELL_Z_SALT)
        );
    }

    private CellBounds bounds(int cellX, int cellZ) {
        long minX = (long) cellX * config.cellSize();
        long minZ = (long) cellZ * config.cellSize();
        long maxX = minX + config.cellSize() - 1L;
        long maxZ = minZ + config.cellSize() - 1L;
        if (minX < Integer.MIN_VALUE
                || minZ < Integer.MIN_VALUE
                || maxX > Integer.MAX_VALUE
                || maxZ > Integer.MAX_VALUE) {
            return null;
        }
        return new CellBounds(
                minX,
                minZ,
                minX + (config.cellSize() - 1L) / 2.0D,
                minZ + (config.cellSize() - 1L) / 2.0D
        );
    }

    private ZoneMultipliers profile(ResourceZone zone) {
        return zoneMultipliers.get(Objects.requireNonNull(zone, "zone"));
    }

    private static Map<ResourceZone, ZoneMultipliers> copyProfiles(
            Map<ResourceZone, ZoneMultipliers> suppliedProfiles
    ) {
        Objects.requireNonNull(suppliedProfiles, "zoneMultipliers");
        EnumMap<ResourceZone, ZoneMultipliers> profiles = new EnumMap<>(ResourceZone.class);
        for (ResourceZone zone : ResourceZone.values()) {
            profiles.put(zone, Objects.requireNonNull(suppliedProfiles.get(zone), "Missing profile for " + zone));
        }
        return Map.copyOf(profiles);
    }

    private static int rangedInt(long seed, int minimum, int maximum) {
        if (minimum == maximum) {
            return minimum;
        }
        int bound = maximum - minimum + 1;
        return minimum + unsignedMod(mix64(seed), bound);
    }

    private static int unsignedMod(long value, int bound) {
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static double unitDouble(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    public record CellCandidate(
            int cellX,
            int cellZ,
            int blockX,
            int blockZ,
            ResourceZone zone,
            ClusterResource resource,
            int reserve,
            int size,
            long shapeSeed
    ) {
    }

    private record CellBounds(long minX, long minZ, double centerX, double centerZ) {
    }

    private record Position(int blockX, int blockZ) {
    }
}
