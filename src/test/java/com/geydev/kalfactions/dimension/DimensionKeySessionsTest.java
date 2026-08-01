package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class DimensionKeySessionsTest {
    @AfterEach
    void clear() {
        DimensionKeySessions.clear();
    }

    @Test
    void actionSessionIsPlayerBoundRateLimitedAndReplaySafe() {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID first = DimensionKeySessions.open(player, 100L);

        assertFalse(DimensionKeySessions.accept(other, first, 104L).isPresent());
        UUID second = DimensionKeySessions.accept(player, first, 104L).orElseThrow();
        assertFalse(DimensionKeySessions.accept(player, first, 108L).isPresent());
        assertFalse(DimensionKeySessions.accept(player, second, 106L).isPresent());
        assertTrue(DimensionKeySessions.accept(player, second, 108L).isPresent());
    }

    @Test
    void actionSessionExpires() {
        UUID player = UUID.randomUUID();
        UUID session = DimensionKeySessions.open(player, 500L);

        assertFalse(DimensionKeySessions.accept(player, session, 1701L).isPresent());
    }
}
