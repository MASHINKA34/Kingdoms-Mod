package com.geydev.kalfactions.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class ClientClaimStore {
    public static final UUID BLACK_ZONE_ID =
            new UUID(0x424C_4143_4B5F_5A4FL, 0x4E45_5F4B_494E_4744L);
    private static final ClaimInfo BLACK_ZONE = new ClaimInfo(
            0x101010,
            "Чёрная зона",
            BLACK_ZONE_ID,
            false,
            false,
            false,
            false,
            false,
            false
    );

    public record ClaimInfo(
            int color,
            String name,
            UUID factionId,
            boolean outpost,
            boolean forceLoaded,
            boolean sanctuary,
            boolean frozen,
            boolean quarry,
            boolean dungeon
    ) {
    }

    public record ViewerInfo(UUID factionId, int claimCount, double claimDiscount) {
        public boolean hasFaction() {
            return factionId != null && (factionId.getMostSignificantBits() != 0L
                    || factionId.getLeastSignificantBits() != 0L);
        }
    }

    public record ZoneInfo(int spawnX, int spawnZ, int redRadius, boolean blackZoneEnabled) {
        public ZoneInfo {
            redRadius = Math.clamp(redRadius, 0, 30_000_000);
        }
    }

    private static final ViewerInfo NO_VIEWER = new ViewerInfo(new UUID(0L, 0L), 0, 0.0D);
    private static final int REGION_SHIFT = 5;

    private static final Map<ResourceKey<Level>, Map<Long, ClaimInfo>> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> REGION_HASHES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, ZoneInfo> ZONES = new ConcurrentHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();
    private static volatile ViewerInfo viewer = NO_VIEWER;

    public static void replace(
            ResourceLocation dimensionId,
            Map<Long, ClaimInfo> claims,
            ViewerInfo viewerInfo,
            ZoneInfo zoneInfo
    ) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (claims.isEmpty()) {
            BY_DIMENSION.remove(dimension);
            REGION_HASHES.remove(dimension);
        } else {
            BY_DIMENSION.put(dimension, Map.copyOf(claims));
            REGION_HASHES.put(dimension, regionHashes(claims));
        }
        if (zoneInfo == null || !zoneInfo.blackZoneEnabled()) {
            ZONES.remove(dimension);
        } else {
            ZONES.put(dimension, zoneInfo);
        }
        viewer = viewerInfo == null ? NO_VIEWER : viewerInfo;
        REVISION.incrementAndGet();
    }

    public static ViewerInfo viewer() {
        return viewer;
    }

    public static ClaimInfo get(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        Map<Long, ClaimInfo> claims = BY_DIMENSION.get(dimension);
        ClaimInfo explicit = claims == null ? null : claims.get(ChunkPos.asLong(chunkX, chunkZ));
        if (explicit != null) {
            return explicit;
        }
        return isBlack(dimension, chunkX, chunkZ) ? BLACK_ZONE : null;
    }

    public static void setForceLoaded(ResourceKey<Level> dimension, long packedChunk, boolean forceLoaded) {
        Map<Long, ClaimInfo> claims = BY_DIMENSION.get(dimension);
        if (claims == null) {
            return;
        }
        ClaimInfo claim = claims.get(packedChunk);
        if (claim == null || claim.forceLoaded() == forceLoaded) {
            return;
        }
        Map<Long, ClaimInfo> updated = new HashMap<>(claims);
        updated.put(
                packedChunk,
                new ClaimInfo(
                        claim.color(),
                        claim.name(),
                        claim.factionId(),
                        claim.outpost(),
                        forceLoaded,
                        claim.sanctuary(),
                        claim.frozen(),
                        claim.quarry(),
                        claim.dungeon())
        );
        BY_DIMENSION.put(dimension, Map.copyOf(updated));
        REGION_HASHES.put(dimension, regionHashes(updated));
        REVISION.incrementAndGet();
    }

    public static Map<Long, ClaimInfo> claims(ResourceKey<Level> dimension) {
        return BY_DIMENSION.getOrDefault(dimension, Map.of());
    }

    public static boolean hasClaims(ResourceKey<Level> dimension) {
        Map<Long, ClaimInfo> claims = BY_DIMENSION.get(dimension);
        return (claims != null && !claims.isEmpty()) || ZONES.containsKey(dimension);
    }

    public static boolean regionHasClaims(ResourceKey<Level> dimension, int regionX, int regionZ) {
        Map<Long, Integer> hashes = REGION_HASHES.get(dimension);
        return (hashes != null && hashes.containsKey(ChunkPos.asLong(regionX, regionZ)))
                || regionIntersectsBlack(dimension, regionX, regionZ);
    }

    public static int regionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        Map<Long, Integer> hashes = REGION_HASHES.get(dimension);
        Integer hash = hashes == null ? null : hashes.get(ChunkPos.asLong(regionX, regionZ));
        int explicit = hash == null ? 0 : hash;
        ZoneInfo zone = ZONES.get(dimension);
        return zone != null && regionIntersectsBlack(dimension, regionX, regionZ)
                ? explicit * 31 + Objects.hash(zone.spawnX(), zone.spawnZ(), zone.redRadius(), 0x080808)
                : explicit;
    }

    public static long revision() {
        return REVISION.get();
    }

    public static void clear() {
        if (!BY_DIMENSION.isEmpty() || !REGION_HASHES.isEmpty() || !ZONES.isEmpty()) {
            BY_DIMENSION.clear();
            REGION_HASHES.clear();
            ZONES.clear();
            viewer = NO_VIEWER;
            REVISION.incrementAndGet();
        }
    }

    private static Map<Long, Integer> regionHashes(Map<Long, ClaimInfo> claims) {
        Map<Long, Integer> hashes = new HashMap<>();
        Set<Long> touchedRegions = new HashSet<>(4);
        for (Map.Entry<Long, ClaimInfo> entry : claims.entrySet()) {
            long chunkKey = entry.getKey();
            ChunkPos pos = new ChunkPos(chunkKey);
            ClaimInfo claim = entry.getValue();
            int claimHash = Long.hashCode(chunkKey) * 31 + claim.color();
            claimHash = claimHash * 31 + claim.factionId().hashCode();
            claimHash = claimHash * 31 + (claim.forceLoaded() ? 1 : 0);
            claimHash = claimHash * 31 + (claim.outpost() ? 1 : 0);
            claimHash = claimHash * 31 + (claim.sanctuary() ? 1 : 0);
            claimHash = claimHash * 31 + (claim.frozen() ? 1 : 0);
            claimHash = claimHash * 31 + (claim.quarry() ? 1 : 0);
            claimHash = claimHash * 31 + (claim.dungeon() ? 1 : 0);
            claimHash = claimHash * 31 + claim.name().hashCode();
            touchedRegions.clear();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    touchedRegions.add(ChunkPos.asLong((pos.x + dx) >> REGION_SHIFT, (pos.z + dz) >> REGION_SHIFT));
                }
            }
            for (Long regionKey : touchedRegions) {
                hashes.merge(regionKey, claimHash, Integer::sum);
            }
        }
        return Map.copyOf(hashes);
    }

    private static boolean isBlack(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ZoneInfo zone = ZONES.get(dimension);
        if (zone == null) {
            return false;
        }
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;
        long distance = Math.max(
                Math.abs((long) blockX - zone.spawnX()),
                Math.abs((long) blockZ - zone.spawnZ())
        );
        return distance > zone.redRadius();
    }

    private static boolean regionIntersectsBlack(ResourceKey<Level> dimension, int regionX, int regionZ) {
        int minChunkX = regionX << REGION_SHIFT;
        int minChunkZ = regionZ << REGION_SHIFT;
        int maxChunkX = minChunkX + (1 << REGION_SHIFT) - 1;
        int maxChunkZ = minChunkZ + (1 << REGION_SHIFT) - 1;
        return isBlack(dimension, minChunkX, minChunkZ)
                || isBlack(dimension, maxChunkX, minChunkZ)
                || isBlack(dimension, minChunkX, maxChunkZ)
                || isBlack(dimension, maxChunkX, maxChunkZ);
    }

    static int priority(ClaimInfo claim) {
        if (claim.dungeon()) {
            return 4;
        }
        if (claim.quarry()) {
            return 3;
        }
        if (claim.sanctuary()) {
            return 2;
        }
        return 1;
    }

    private ClientClaimStore() {
    }
}
