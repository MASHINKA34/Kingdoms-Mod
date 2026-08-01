package com.geydev.kalfactions.dimension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DimensionKeySessions {
    private static final long SESSION_LIFETIME_TICKS = 1_200L;
    private static final long ACTION_RATE_LIMIT_TICKS = 4L;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static UUID open(UUID playerId, long serverTick) {
        UUID id = UUID.randomUUID();
        SESSIONS.put(playerId, new Session(
                id,
                serverTick + SESSION_LIFETIME_TICKS,
                serverTick - ACTION_RATE_LIMIT_TICKS
        ));
        return id;
    }

    public static Optional<UUID> accept(UUID playerId, UUID sessionId, long serverTick) {
        Session session = SESSIONS.get(playerId);
        if (session == null || !session.id().equals(sessionId) || session.expiresAtTick() < serverTick
                || serverTick - session.lastActionTick() < ACTION_RATE_LIMIT_TICKS) {
            return Optional.empty();
        }
        UUID next = UUID.randomUUID();
        SESSIONS.put(playerId, new Session(next, serverTick + SESSION_LIFETIME_TICKS, serverTick));
        return Optional.of(next);
    }

    public static void remove(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    private record Session(UUID id, long expiresAtTick, long lastActionTick) {
    }

    private DimensionKeySessions() {
    }
}
