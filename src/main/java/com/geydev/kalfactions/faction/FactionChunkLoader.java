package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * The chunk-ticket half of force loading. {@link FactionManager} owns what a faction wants loaded;
 * this owns what the game is actually holding open, so the two can drift apart (a suspended faction,
 * a dimension that is gone, a claim that was sold) and be reconciled instead of leaking tickets.
 */
final class FactionChunkLoader {
    private static final TicketController TICKETS = new TicketController(
        ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "faction_chunks"),
        FactionChunkLoader::validateTickets
    );

    private final Map<ClaimKey, UUID> applied = new HashMap<>();
    private final Set<UUID> suspended = new HashSet<>();
    private boolean migrated;

    static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(TICKETS);
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        FactionManager manager = FactionManager.get(level);
        helper.getEntityTickets().forEach((factionId, tickets) -> {
            for (long packedChunk : tickets.nonTicking()) {
                helper.removeTicket(factionId, packedChunk, false);
            }
            Faction faction = manager.getFactionById(factionId).orElse(null);
            for (long packedChunk : tickets.ticking()) {
                ClaimKey key = new ClaimKey(level.dimension(), new ChunkPos(packedChunk));
                if (faction == null || !faction.isForceLoaded(key)) {
                    helper.removeTicket(factionId, packedChunk, true);
                }
            }
        });
    }

    boolean isSuspended(UUID factionId) {
        return suspended.contains(factionId);
    }

    void setSuspended(UUID factionId, boolean value) {
        if (value) {
            suspended.add(factionId);
        } else {
            suspended.remove(factionId);
        }
    }

    boolean isApplied(ClaimKey key, UUID factionId) {
        return factionId.equals(applied.get(key));
    }

    int appliedCount() {
        return applied.size();
    }

    boolean migrated() {
        return migrated;
    }

    void markMigrated() {
        migrated = true;
    }

    void apply(ServerLevel level, UUID factionId, ClaimKey key) {
        TICKETS.forceChunk(level, factionId, key.x(), key.z(), true, true);
        applied.put(key, factionId);
    }

    void release(MinecraftServer server, UUID factionId, ClaimKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level != null) {
            TICKETS.forceChunk(level, factionId, key.x(), key.z(), false, true);
        }
        applied.remove(key);
    }

    /**
     * Brings the held tickets in line with what the factions ask for. Returns true when a faction's
     * own state had to be corrected, so the caller knows to mark its saved data dirty.
     */
    boolean reconcile(MinecraftServer server, Collection<Faction> factions) {
        boolean factionStateChanged = false;
        Map<ClaimKey, UUID> desired = new HashMap<>();
        for (Faction faction : factions) {
            boolean paused = suspended.contains(faction.id());
            for (ClaimKey key : faction.forceLoadedChunks()) {
                if (!faction.hasClaim(key) && !faction.isOutpostChunk(key)) {
                    factionStateChanged |= faction.removeForceLoaded(key);
                    continue;
                }
                if (paused) {
                    release(server, faction.id(), key);
                    continue;
                }
                desired.put(key, faction.id());
            }
        }

        if (!migrated) {
            for (ClaimKey key : desired.keySet()) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level != null) {
                    level.setChunkForced(key.x(), key.z(), false);
                }
            }
            migrated = true;
            factionStateChanged = true;
        }

        for (Map.Entry<ClaimKey, UUID> entry : new ArrayList<>(applied.entrySet())) {
            if (!entry.getValue().equals(desired.get(entry.getKey()))) {
                release(server, entry.getValue(), entry.getKey());
            }
        }
        for (Map.Entry<ClaimKey, UUID> entry : desired.entrySet()) {
            ClaimKey key = entry.getKey();
            UUID factionId = entry.getValue();
            if (factionId.equals(applied.get(key))) {
                continue;
            }
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null) {
                apply(level, factionId, key);
            }
        }
        return factionStateChanged;
    }
}
