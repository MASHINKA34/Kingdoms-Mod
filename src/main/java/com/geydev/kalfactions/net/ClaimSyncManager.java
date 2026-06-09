package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.integration.IntegrationManager.FactionMapData;
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

    private static final Map<UUID, SyncState> STATES = new HashMap<>();
    private static int ticksUntilCheck;

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
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
        SyncState previous = STATES.get(player.getUUID());
        if (previous != null
                && previous.dimension.equals(dimension)
                && previous.revision == revision
                && within(previous.chunkPos, chunkPos)) {
            return;
        }
        sendTo(player);
    }

    private static void sendTo(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        ChunkPos center = player.chunkPosition();
        int radius = ModConfigSpec.CLAIM_SYNC_RADIUS_CHUNKS.get();
        List<FactionPayloads.ClaimEntry> entries = new ArrayList<>();

        outer:
        for (FactionMapData faction : IntegrationManager.snapshots()) {
            for (ClaimKey claim : faction.claims()) {
                if (!claim.dimension().equals(dimension)) {
                    continue;
                }
                if (Math.abs(claim.x() - center.x) > radius || Math.abs(claim.z() - center.z) > radius) {
                    continue;
                }
                entries.add(new FactionPayloads.ClaimEntry(
                        claim.x(),
                        claim.z(),
                        faction.color(),
                        faction.name(),
                        faction.factionId()
                ));
                if (entries.size() >= FactionPayloads.S2CSyncClaims.MAX_ENTRIES) {
                    break outer;
                }
            }
        }

        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CSyncClaims(dimension.location(), entries));
        STATES.put(player.getUUID(), new SyncState(dimension, center.toLong(), IntegrationManager.revision()));
    }

    private static boolean within(long previousChunk, long currentChunk) {
        ChunkPos previous = new ChunkPos(previousChunk);
        ChunkPos current = new ChunkPos(currentChunk);
        return Math.abs(previous.x - current.x) < RESEND_MOVE_CHUNKS
                && Math.abs(previous.z - current.z) < RESEND_MOVE_CHUNKS;
    }

    private record SyncState(ResourceKey<Level> dimension, long chunkPos, long revision) {
    }

    private ClaimSyncManager() {
    }
}
