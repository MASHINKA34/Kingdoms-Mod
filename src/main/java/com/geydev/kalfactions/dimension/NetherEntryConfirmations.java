package com.geydev.kalfactions.dimension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NetherEntryConfirmations {
    public static final long CONFIRMATION_WINDOW_TICKS = 300L;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void request(UUID playerId, UUID factionId, long serverTick) {
        PENDING.put(playerId, new Pending(factionId, serverTick + CONFIRMATION_WINDOW_TICKS));
    }

    public static boolean consume(
            UUID playerId,
            UUID factionId,
            long serverTick,
            boolean explicitGesture
    ) {
        Pending pending = PENDING.get(playerId);
        if (pending == null || pending.expiresAtTick() < serverTick || !pending.factionId().equals(factionId)) {
            PENDING.remove(playerId);
            return false;
        }
        if (!explicitGesture) {
            return false;
        }
        PENDING.remove(playerId);
        return true;
    }

    public static boolean isPending(UUID playerId, UUID factionId, long serverTick) {
        Pending pending = PENDING.get(playerId);
        return pending != null && pending.expiresAtTick() >= serverTick && pending.factionId().equals(factionId);
    }

    public static void remove(UUID playerId) {
        PENDING.remove(playerId);
    }

    public static void clear() {
        PENDING.clear();
    }

    private record Pending(UUID factionId, long expiresAtTick) {
    }

    private NetherEntryConfirmations() {
    }
}
