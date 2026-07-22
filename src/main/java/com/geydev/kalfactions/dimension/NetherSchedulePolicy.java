package com.geydev.kalfactions.dimension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class NetherSchedulePolicy {
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    public static final LocalTime OPENS_AT = LocalTime.of(18, 0);
    public static final LocalTime CLOSES_AT = LocalTime.of(23, 0);

    public static boolean isOpen(Instant now) {
        LocalTime time = now.atZone(MOSCOW).toLocalTime();
        return !time.isBefore(OPENS_AT) && time.isBefore(CLOSES_AT);
    }

    public static boolean canStartSession(Instant now, Duration duration, boolean requireFullSession) {
        if (!isOpen(now)) {
            return false;
        }
        return !requireFullSession || !now.plus(duration).isAfter(closeInstant(now));
    }

    public static Instant closeInstant(Instant now) {
        ZonedDateTime moscow = now.atZone(MOSCOW);
        return ZonedDateTime.of(moscow.toLocalDate(), CLOSES_AT, MOSCOW).toInstant();
    }

    public static LocalDate date(Instant now) {
        return now.atZone(MOSCOW).toLocalDate();
    }

    public static long secondsUntilClose(Instant now) {
        if (!isOpen(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, closeInstant(now)).getSeconds());
    }

    private NetherSchedulePolicy() {
    }
}
