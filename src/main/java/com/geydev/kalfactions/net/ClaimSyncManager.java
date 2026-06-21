package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.integration.IntegrationManager.FactionMapData;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ClaimSyncManager {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int RESEND_MOVE_CHUNKS = 8;
    private static final int ROGUE_COLOR = 0x111111;
    private static final String ROGUE_NAME = "Форпост захвачен рейдерами";

    private static final Map<UUID, SyncState> STATES = new HashMap<>();
    private static int ticksUntilCheck;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        FactionServerHooks.clearRateLimit(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilCheck > 0) {
            return;
        }
        ticksUntilCheck = CHECK_INTERVAL_TICKS;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            maybeSend(player);
        }
    }

    private static void maybeSend(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        long chunkPos = player.chunkPosition().toLong();
        long revision = IntegrationManager.revision();
        UUID viewerFactionId = FactionManager.get(player.serverLevel())
                .getFactionIdForMember(player.getUUID())
                .orElse(FactionSnapshot.NO_FACTION);
        SyncState previous = STATES.get(player.getUUID());
        if (previous != null
                && previous.dimension.equals(dimension)
                && previous.revision == revision
                && previous.viewerFactionId.equals(viewerFactionId)
                && within(previous.chunkPos, chunkPos)) {
            return;
        }
        sendTo(player);
    }

    public static void resync(ServerPlayer player) {
        sendTo(player);
    }

    private static void sendTo(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        long revision = IntegrationManager.revision();
        List<FactionPayloads.ClaimEntry> entries = new ArrayList<>();

        outer:
        for (FactionMapData faction : IntegrationManager.snapshots()) {
            for (ClaimKey claim : faction.claims()) {
                if (!claim.dimension().equals(dimension)) {
                    continue;
                }
                entries.add(new FactionPayloads.ClaimEntry(
                        claim.x(),
                        claim.z(),
                        faction.color(),
                        faction.name(),
                        faction.factionId(),
                        faction.isOutpost(claim),
                        faction.isForceLoaded(claim)
                ));
                if (entries.size() >= FactionPayloads.S2CSyncClaims.MAX_ENTRIES) {
                    break outer;
                }
            }
        }

        rogue:
        for (RogueOutpostManager.RogueOutpost rogueOutpost : RogueOutpostManager.get(player.serverLevel()).all()) {
            for (ClaimKey chunk : rogueOutpost.chunks()) {
                if (!chunk.dimension().equals(dimension)) {
                    continue;
                }
                if (entries.size() >= FactionPayloads.S2CSyncClaims.MAX_ENTRIES) {
                    break rogue;
                }
                entries.add(new FactionPayloads.ClaimEntry(
                        chunk.x(),
                        chunk.z(),
                        ROGUE_COLOR,
                        ROGUE_NAME,
                        RogueOutpostManager.ROGUE_FACTION_ID,
                        false,
                        false
                ));
            }
        }

        Faction viewerFaction = FactionManager.get(player.serverLevel())
                .getFactionForMember(player.getUUID())
                .orElse(null);
        UUID viewerFactionId = viewerFaction == null ? FactionSnapshot.NO_FACTION : viewerFaction.id();
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CSyncClaims(
                dimension.location(),
                entries,
                viewerFactionId,
                viewerFaction == null ? 0 : viewerFaction.claimCount(),
                viewerFaction == null ? 0.0D : viewerFaction.claimDiscount()
        ));
        STATES.put(player.getUUID(), new SyncState(dimension, player.chunkPosition().toLong(), revision, viewerFactionId));
    }

    private static boolean within(long previousChunk, long currentChunk) {
        ChunkPos previous = new ChunkPos(previousChunk);
        ChunkPos current = new ChunkPos(currentChunk);
        return Math.abs(previous.x - current.x) < RESEND_MOVE_CHUNKS
                && Math.abs(previous.z - current.z) < RESEND_MOVE_CHUNKS;
    }

    private record SyncState(ResourceKey<Level> dimension, long chunkPos, long revision, UUID viewerFactionId) {
    }

    private ClaimSyncManager() {
    }
}
