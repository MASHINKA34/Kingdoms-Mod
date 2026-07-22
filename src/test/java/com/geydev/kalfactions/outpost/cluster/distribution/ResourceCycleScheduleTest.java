package com.geydev.kalfactions.outpost.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

final class ResourceCycleScheduleTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Test
    void schedulesSevenDayCycleAtMoscowMidnight() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 22, 13, 15, 0, 0, MOSCOW);
        long next = ResourceCycleSchedule.next(now.toInstant().toEpochMilli(), 7, 0, MOSCOW);

        assertEquals(ZonedDateTime.of(2026, 7, 29, 0, 0, 0, 0, MOSCOW).toInstant().toEpochMilli(), next);
    }

    @Test
    void usesUpcomingResetWhenBeforeConfiguredHour() {
        ZonedDateTime now = ZonedDateTime.of(2026, 7, 22, 2, 0, 0, 0, MOSCOW);
        long next = ResourceCycleSchedule.next(now.toInstant().toEpochMilli(), 7, 6, MOSCOW);

        assertEquals(ZonedDateTime.of(2026, 7, 22, 6, 0, 0, 0, MOSCOW).toInstant().toEpochMilli(), next);
    }
}
