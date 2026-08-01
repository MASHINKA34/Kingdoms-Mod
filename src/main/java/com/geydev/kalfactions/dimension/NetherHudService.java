package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.dimension.DimensionControlManager.ActiveSession;
import com.geydev.kalfactions.faction.FactionManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetherHudService {
    private static final long RESYNC_INTERVAL_TICKS = 200L;
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private static final Map<UUID, SentState> SENT = new HashMap<>();

    public static void tick(MinecraftServer server, Instant now) {
        long tick = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID factionId = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
            DimensionPayloads.S2CNetherHud payload = payloadFor(
                    DimensionControlManager.get(server), factionId, player.getUUID(), now, PREVIEWS.get(player.getUUID())
            );
            SentState previous = SENT.get(player.getUUID());
            HudSignature signature = HudSignature.from(payload);
            if (previous == null || !previous.signature().equals(signature)
                    || tick - previous.sentAtTick() >= RESYNC_INTERVAL_TICKS) {
                PacketDistributor.sendToPlayer(player, payload);
                SENT.put(player.getUUID(), new SentState(signature, tick));
            }
        }
        PREVIEWS.entrySet().removeIf(entry -> !entry.getValue().endsAt().isAfter(now));
    }

    public static void syncNow(ServerPlayer player, Instant now) {
        MinecraftServer server = player.serverLevel().getServer();
        UUID factionId = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
        DimensionPayloads.S2CNetherHud payload = payloadFor(
                DimensionControlManager.get(server), factionId, player.getUUID(), now, PREVIEWS.get(player.getUUID())
        );
        PacketDistributor.sendToPlayer(player, payload);
        SENT.put(player.getUUID(), new SentState(HudSignature.from(payload), server.getTickCount()));
    }

    public static void preview(ServerPlayer player, boolean opening, int seconds) {
        Instant now = Instant.now();
        PREVIEWS.put(player.getUUID(), new Preview(opening, now.plusSeconds(seconds)));
        SENT.remove(player.getUUID());
        syncNow(player, now);
    }

    public static void clearPreview(ServerPlayer player) {
        PREVIEWS.remove(player.getUUID());
        SENT.remove(player.getUUID());
        syncNow(player, Instant.now());
    }

    public static void removePlayer(UUID playerId) {
        PREVIEWS.remove(playerId);
        SENT.remove(playerId);
    }

    public static void clear() {
        PREVIEWS.clear();
        SENT.clear();
    }

    static DimensionPayloads.S2CNetherHud realPayload(
            DimensionControlManager control,
            UUID factionId,
            UUID playerId,
            Instant now
    ) {
        return payloadFor(control, factionId, playerId, now, null);
    }

    static DimensionPayloads.S2CNetherHud previewPayload(
            DimensionControlManager control,
            UUID factionId,
            UUID playerId,
            Instant now,
            boolean opening,
            int seconds
    ) {
        return payloadFor(control, factionId, playerId, now, new Preview(opening, now.plusSeconds(seconds)));
    }

    private static DimensionPayloads.S2CNetherHud payloadFor(
            DimensionControlManager control,
            UUID factionId,
            UUID playerId,
            Instant now,
            Preview preview
    ) {
        int total = control.rules().sessionsPerDay();
        int remaining = factionId == null ? -1 : control.remainingSessions(factionId, now);
        if (preview != null && preview.endsAt().isAfter(now)) {
            long sessionEnd = !preview.opening() && factionId != null ? preview.endsAt().toEpochMilli() : 0L;
            return new DimensionPayloads.S2CNetherHud(
                    true,
                    preview.opening(),
                    true,
                    now.toEpochMilli(),
                    preview.endsAt().toEpochMilli(),
                    remaining,
                    total,
                    sessionEnd
            );
        }
        boolean visible = !control.isClosed(Level.NETHER) && NetherSchedulePolicy.isHudVisible(now);
        boolean opening = visible && !NetherSchedulePolicy.isOpen(now);
        long phaseEnd = !visible ? 0L : (opening
                ? NetherSchedulePolicy.openInstant(now).toEpochMilli()
                : NetherSchedulePolicy.closeInstant(now).toEpochMilli());
        long sessionEnd = 0L;
        if (visible && !opening && factionId != null) {
            ActiveSession active = control.assignedSession(playerId, now)
                    .or(() -> control.activeSession(factionId, now))
                    .orElse(null);
            if (active != null && active.factionId().equals(factionId)) {
                sessionEnd = active.endsAt().toEpochMilli();
            }
        }
        return new DimensionPayloads.S2CNetherHud(
                visible,
                opening,
                false,
                now.toEpochMilli(),
                phaseEnd,
                remaining,
                total,
                sessionEnd
        );
    }

    private record Preview(boolean opening, Instant endsAt) {
    }

    private record SentState(HudSignature signature, long sentAtTick) {
    }

    private record HudSignature(
            boolean visible,
            boolean opening,
            boolean preview,
            long phaseEndsAtEpochMillis,
            int remainingSessions,
            int totalSessions,
            long sessionEndsAtEpochMillis
    ) {
        private static HudSignature from(DimensionPayloads.S2CNetherHud payload) {
            return new HudSignature(
                    payload.visible(), payload.opening(), payload.preview(), payload.phaseEndsAtEpochMillis(),
                    payload.remainingSessions(), payload.totalSessions(), payload.sessionEndsAtEpochMillis()
            );
        }
    }

    private NetherHudService() {
    }
}
