package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class NetherEntryConfirmationsTest {
    @AfterEach
    void clear() {
        NetherEntryConfirmations.clear();
    }

    @Test
    void confirmationRequiresMatchingFactionExplicitGestureAndIsSingleUse() {
        UUID player = UUID.randomUUID();
        UUID faction = UUID.randomUUID();
        NetherEntryConfirmations.request(player, faction, 100L);

        assertFalse(NetherEntryConfirmations.consume(player, faction, 101L, false));
        assertFalse(NetherEntryConfirmations.consume(player, UUID.randomUUID(), 101L, true));

        NetherEntryConfirmations.request(player, faction, 102L);
        assertTrue(NetherEntryConfirmations.consume(player, faction, 103L, true));
        assertFalse(NetherEntryConfirmations.consume(player, faction, 104L, true));
    }

    @Test
    void confirmationExpiresAfterFifteenSeconds() {
        UUID player = UUID.randomUUID();
        UUID faction = UUID.randomUUID();
        NetherEntryConfirmations.request(player, faction, 500L);

        assertTrue(NetherEntryConfirmations.isPending(player, faction, 800L));
        assertFalse(NetherEntryConfirmations.consume(player, faction, 801L, true));
    }
}
