package com.geydev.kalfactions.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

final class ActionCooldownTest {
    private static final long COOLDOWN = 2L;

    @Test
    void firstActionIsAllowed() {
        Map<UUID, Long> ticks = new ConcurrentHashMap<>();
        UUID player = UUID.randomUUID();

        assertNull(ActionCooldown.mark(ticks, player, 100L, COOLDOWN));
    }

    @Test
    void secondActionInTheSameTickIsBlocked() {
        Map<UUID, Long> ticks = new ConcurrentHashMap<>();
        UUID player = UUID.randomUUID();
        ActionCooldown.mark(ticks, player, 100L, COOLDOWN);

        Long blocked = ActionCooldown.mark(ticks, player, 100L, COOLDOWN);

        assertNotNull(blocked);
        assertEquals(100L, blocked);
    }

    @Test
    void spammingDoesNotPushTheWindowForward() {
        Map<UUID, Long> ticks = new ConcurrentHashMap<>();
        UUID player = UUID.randomUUID();
        ActionCooldown.mark(ticks, player, 100L, COOLDOWN);

        assertNotNull(ActionCooldown.mark(ticks, player, 101L, COOLDOWN));

        assertNull(ActionCooldown.mark(ticks, player, 102L, COOLDOWN));
    }

    @Test
    void actionIsAllowedAgainOnceTheCooldownElapsed() {
        Map<UUID, Long> ticks = new ConcurrentHashMap<>();
        UUID player = UUID.randomUUID();
        ActionCooldown.mark(ticks, player, 100L, COOLDOWN);

        assertNull(ActionCooldown.mark(ticks, player, 102L, COOLDOWN));
        assertNotNull(ActionCooldown.mark(ticks, player, 103L, COOLDOWN));
    }

    @Test
    void playersAreLimitedIndependently() {
        Map<UUID, Long> ticks = new ConcurrentHashMap<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ActionCooldown.mark(ticks, first, 100L, COOLDOWN);

        assertNull(ActionCooldown.mark(ticks, second, 100L, COOLDOWN));
    }
}
