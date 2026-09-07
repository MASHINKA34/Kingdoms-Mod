package com.geydev.kalfactions.net;

import java.util.Map;
import java.util.UUID;

public final class ActionCooldown {
    public static Long mark(Map<UUID, Long> lastActionTick, UUID playerId, long now, long cooldownTicks) {
        Long previous = lastActionTick.putIfAbsent(playerId, now);
        if (previous == null) {
            return null;
        }
        if (now - previous < cooldownTicks) {
            return previous;
        }
        return lastActionTick.replace(playerId, previous, now) ? null : Long.valueOf(now);
    }

    private ActionCooldown() {
    }
}
