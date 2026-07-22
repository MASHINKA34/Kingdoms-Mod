package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class NetherSchedulePolicyTest {
    @Test
    void moscowWindowHasInclusiveOpeningAndExclusiveClosing() {
        assertFalse(NetherSchedulePolicy.isOpen(Instant.parse("2026-07-22T14:59:59Z")));
        assertTrue(NetherSchedulePolicy.isOpen(Instant.parse("2026-07-22T15:00:00Z")));
        assertTrue(NetherSchedulePolicy.isOpen(Instant.parse("2026-07-22T19:59:59Z")));
        assertFalse(NetherSchedulePolicy.isOpen(Instant.parse("2026-07-22T20:00:00Z")));
    }

    @Test
    void fullSessionMustStartNoLaterThanTwentyOneThirtyMoscow() {
        Duration duration = Duration.ofMinutes(90);

        assertTrue(NetherSchedulePolicy.canStartSession(
                Instant.parse("2026-07-22T18:30:00Z"), duration, true
        ));
        assertFalse(NetherSchedulePolicy.canStartSession(
                Instant.parse("2026-07-22T18:30:01Z"), duration, true
        ));
        assertTrue(NetherSchedulePolicy.canStartSession(
                Instant.parse("2026-07-22T19:30:00Z"), duration, false
        ));
    }
}
