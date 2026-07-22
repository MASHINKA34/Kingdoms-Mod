package com.geydev.kalfactions.outpost.trader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class TradeSessionManager {
    private static final long SESSION_TTL_MILLIS = 5L * 60L * 1000L;
    private static final Map<MinecraftServer, Map<UUID, Session>> SESSIONS = new WeakHashMap<>();

    public static synchronized UUID open(ServerPlayer player, UUID traderId) {
        cleanup(player.getServer());
        UUID sessionId = UUID.randomUUID();
        sessions(player.getServer()).put(
                player.getUUID(),
                new Session(sessionId, traderId, System.currentTimeMillis() + SESSION_TTL_MILLIS, 0L)
        );
        return sessionId;
    }

    public static synchronized Validation validate(
            ServerPlayer player,
            UUID traderId,
            UUID sessionId,
            long sequence
    ) {
        if (sequence < 1L) {
            return Validation.INVALID;
        }
        Session session = sessions(player.getServer()).get(player.getUUID());
        long now = System.currentTimeMillis();
        if (session == null || session.expiresAt < now || !session.sessionId.equals(sessionId)
                || !session.traderId.equals(traderId)) {
            return Validation.INVALID;
        }
        if (!session.sequenceGuard.accept(sequence)) {
            return Validation.REPLAY;
        }
        session.expiresAt = now + SESSION_TTL_MILLIS;
        return Validation.ACCEPTED;
    }

    public static synchronized boolean refresh(ServerPlayer player, UUID traderId, UUID sessionId) {
        Session session = sessions(player.getServer()).get(player.getUUID());
        long now = System.currentTimeMillis();
        if (session == null || session.expiresAt < now || !session.sessionId.equals(sessionId)
                || !session.traderId.equals(traderId)) {
            return false;
        }
        session.expiresAt = now + SESSION_TTL_MILLIS;
        return true;
    }

    public static synchronized void close(ServerPlayer player, UUID traderId, UUID sessionId) {
        Map<UUID, Session> sessions = sessions(player.getServer());
        Session session = sessions.get(player.getUUID());
        if (session != null && session.traderId.equals(traderId) && session.sessionId.equals(sessionId)) {
            sessions.remove(player.getUUID());
        }
    }

    public static synchronized void clear(MinecraftServer server) {
        SESSIONS.remove(server);
    }

    public static synchronized Snapshot snapshot(ServerPlayer player, UUID traderId) {
        Session session = sessions(player.getServer()).get(player.getUUID());
        if (session == null || session.expiresAt < System.currentTimeMillis() || !session.traderId.equals(traderId)) {
            return new Snapshot(new UUID(0L, 0L), 0L);
        }
        return new Snapshot(session.sessionId, session.sequenceGuard.lastAccepted());
    }

    private static Map<UUID, Session> sessions(MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, ignored -> new HashMap<>());
    }

    private static void cleanup(MinecraftServer server) {
        long now = System.currentTimeMillis();
        sessions(server).values().removeIf(session -> session.expiresAt < now);
    }

    public enum Validation {
        ACCEPTED,
        REPLAY,
        INVALID
    }

    public record Snapshot(UUID sessionId, long acknowledgedSequence) {
    }

    private static final class Session {
        private final UUID sessionId;
        private final UUID traderId;
        private long expiresAt;
        private final TradeSequenceGuard sequenceGuard = new TradeSequenceGuard();

        private Session(UUID sessionId, UUID traderId, long expiresAt, long lastSequence) {
            this.sessionId = sessionId;
            this.traderId = traderId;
            this.expiresAt = expiresAt;
            if (lastSequence > 0L) {
                this.sequenceGuard.accept(lastSequence);
            }
        }
    }

    private TradeSessionManager() {
    }
}
