package com.geydev.kalfactions.profiling;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class FrozenChunks {
    private static volatile Map<ResourceKey<Level>, LongSet> frozen = Map.of();

    public static boolean isEmpty() {
        return frozen.isEmpty();
    }

    public static boolean contains(ResourceKey<Level> dimension, long packedChunk) {
        LongSet chunks = frozen.get(dimension);
        return chunks != null && chunks.contains(packedChunk);
    }

    public static void rebuild(MinecraftServer server, Set<UUID> frozenFactionIds) {
        if (frozenFactionIds.isEmpty()) {
            frozen = Map.of();
            return;
        }
        FactionManager manager = FactionManager.get(server);
        Map<ResourceKey<Level>, LongSet> updated = new HashMap<>();
        for (UUID factionId : frozenFactionIds) {
            Faction faction = manager.getFactionById(factionId).orElse(null);
            if (faction == null) {
                continue;
            }
            for (ClaimKey claim : faction.claims()) {
                updated.computeIfAbsent(claim.dimension(), ignored -> new LongOpenHashSet())
                    .add(claim.chunk().toLong());
            }
            for (ClaimKey claim : faction.outpostChunks()) {
                updated.computeIfAbsent(claim.dimension(), ignored -> new LongOpenHashSet())
                    .add(claim.chunk().toLong());
            }
        }
        frozen = Map.copyOf(updated);
    }

    public static void clear() {
        frozen = Map.of();
    }

    private FrozenChunks() {
    }
}
