package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void lateSessionsRemainAvailableAndAreClampedToClosing() {
        Duration duration = Duration.ofMinutes(90);

        Instant late = Instant.parse("2026-07-22T18:45:00Z");
        Instant veryLate = Instant.parse("2026-07-22T19:50:00Z");
        assertTrue(NetherSchedulePolicy.canStartSession(late));
        assertTrue(NetherSchedulePolicy.canStartSession(veryLate));
        assertEquals(Instant.parse("2026-07-22T20:00:00Z"), NetherSchedulePolicy.sessionEnd(late, duration));
        assertEquals(Instant.parse("2026-07-22T20:00:00Z"), NetherSchedulePolicy.sessionEnd(veryLate, duration));
    }

    @Test
    void nextOpeningUsesTodayBeforeEighteenAndTomorrowAfterwards() {
        assertEquals(
                Instant.parse("2026-07-22T15:00:00Z"),
                NetherSchedulePolicy.nextOpenInstant(Instant.parse("2026-07-22T14:55:00Z"))
        );
        assertEquals(
                Instant.parse("2026-07-23T15:00:00Z"),
                NetherSchedulePolicy.nextOpenInstant(Instant.parse("2026-07-22T15:00:00Z"))
        );
    }
}
