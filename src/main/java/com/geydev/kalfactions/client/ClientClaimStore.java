package com.geydev.kalfactions.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    public record ClaimInfo(int color, String name, UUID factionId, boolean outpost) {
    }

    public record ViewerInfo(UUID factionId, int claimCount, double claimDiscount) {
        public boolean hasFaction() {
            return factionId != null && (factionId.getMostSignificantBits() != 0L
                    || factionId.getLeastSignificantBits() != 0L);
        }
    }

    private static final ViewerInfo NO_VIEWER = new ViewerInfo(new UUID(0L, 0L), 0, 0.0D);
    private static final int REGION_SHIFT = 5;

    private static final Map<ResourceKey<Level>, Map<Long, ClaimInfo>> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Integer>> REGION_HASHES = new ConcurrentHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();
    private static volatile ViewerInfo viewer = NO_VIEWER;

    public static void replace(ResourceLocation dimensionId, Map<Long, ClaimInfo> claims, ViewerInfo viewerInfo) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (claims.isEmpty()) {
            BY_DIMENSION.remove(dimension);
            REGION_HASHES.remove(dimension);
        } else {
            BY_DIMENSION.put(dimension, Map.copyOf(claims));
            REGION_HASHES.put(dimension, regionHashes(claims));
        }
        viewer = viewerInfo == null ? NO_VIEWER : viewerInfo;
        REVISION.incrementAndGet();
    }

    public static ViewerInfo viewer() {
        return viewer;
    }

    public static ClaimInfo get(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        Map<Long, ClaimInfo> claims = BY_DIMENSION.get(dimension);
        return claims == null ? null : claims.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static Map<Long, ClaimInfo> claims(ResourceKey<Level> dimension) {
        return BY_DIMENSION.getOrDefault(dimension, Map.of());
    }

    public static boolean hasClaims(ResourceKey<Level> dimension) {
        Map<Long, ClaimInfo> claims = BY_DIMENSION.get(dimension);
        return claims != null && !claims.isEmpty();
    }

    public static boolean regionHasClaims(ResourceKey<Level> dimension, int regionX, int regionZ) {
        Map<Long, Integer> hashes = REGION_HASHES.get(dimension);
        return hashes != null && hashes.containsKey(ChunkPos.asLong(regionX, regionZ));
    }

    public static int regionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        Map<Long, Integer> hashes = REGION_HASHES.get(dimension);
        if (hashes == null) {
            return 0;
        }
        Integer hash = hashes.get(ChunkPos.asLong(regionX, regionZ));
        return hash == null ? 0 : hash;
    }

    public static long revision() {
        return REVISION.get();
    }

    public static void clear() {
        if (!BY_DIMENSION.isEmpty() || !REGION_HASHES.isEmpty()) {
            BY_DIMENSION.clear();
            REGION_HASHES.clear();
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

    private ClientClaimStore() {
    }
}
