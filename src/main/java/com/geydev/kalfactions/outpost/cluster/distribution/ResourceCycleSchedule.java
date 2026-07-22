package com.geydev.kalfactions.outpost.cluster.distribution;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class ResourceCycleSchedule {
    public static long next(long nowMillis, int cycleDays, int resetHour, ZoneId zoneId) {
        if (cycleDays < 1 || cycleDays > 365) {
            throw new IllegalArgumentException("cycleDays must be between 1 and 365");
        }
        if (resetHour < 0 || resetHour > 23) {
            throw new IllegalArgumentException("resetHour must be between 0 and 23");
        }
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(Objects.requireNonNull(zoneId, "zoneId"));
        ZonedDateTime next = now.truncatedTo(ChronoUnit.DAYS).withHour(resetHour);
        while (!next.isAfter(now)) {
            next = next.plusDays(cycleDays);
        }
        return next.toInstant().toEpochMilli();
    }

    private ResourceCycleSchedule() {
    }
}
