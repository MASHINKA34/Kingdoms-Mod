package com.geydev.kalfactions.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public final class ClientClaimStore {
    public record ClaimInfo(int color, String name, UUID factionId) {
    }

    private static final Map<ResourceKey<Level>, Map<Long, ClaimInfo>> BY_DIMENSION = new ConcurrentHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();

    public static void replace(ResourceLocation dimensionId, Map<Long, ClaimInfo> claims) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (claims.isEmpty()) {
            BY_DIMENSION.remove(dimension);
        } else {
            BY_DIMENSION.put(dimension, Map.copyOf(claims));
        }
        REVISION.incrementAndGet();
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

    public static long revision() {
        return REVISION.get();
    }

    public static void clear() {
        if (!BY_DIMENSION.isEmpty()) {
            BY_DIMENSION.clear();
            REVISION.incrementAndGet();
        }
    }

    private ClientClaimStore() {
    }
}
