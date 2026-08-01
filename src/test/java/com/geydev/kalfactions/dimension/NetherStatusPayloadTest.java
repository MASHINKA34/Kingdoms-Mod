package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class NetherStatusPayloadTest {
    @Test
    void statusSupportsNoFactionAndLivePhaseDeadline() {
        var payload = new DimensionPayloads.S2CNetherStatus(
                1_000L, 10_000L, false, false, -1, 2, 0L
        );

        assertEquals(-1, payload.remainingSessions());
        assertEquals(10_000L, payload.phaseEndsAtEpochMillis());
    }

    @Test
    void statusRejectsImpossibleSessionCounts() {
        assertThrows(IllegalArgumentException.class, () -> new DimensionPayloads.S2CNetherStatus(
                1_000L, 10_000L, false, false, 3, 2, 0L
        ));
    }
}
