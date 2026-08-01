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
    public static final LocalTime HUD_STARTS_AT = OPENS_AT.minusMinutes(5);

    public static boolean isOpen(Instant now) {
        LocalTime time = now.atZone(MOSCOW).toLocalTime();
        return !time.isBefore(OPENS_AT) && time.isBefore(CLOSES_AT);
    }

    public static boolean canStartSession(Instant now) {
        return isOpen(now);
    }

    public static boolean isHudVisible(Instant now) {
        LocalTime time = now.atZone(MOSCOW).toLocalTime();
        return !time.isBefore(HUD_STARTS_AT) && time.isBefore(CLOSES_AT);
    }

    public static Instant openInstant(Instant now) {
        ZonedDateTime moscow = now.atZone(MOSCOW);
        return ZonedDateTime.of(moscow.toLocalDate(), OPENS_AT, MOSCOW).toInstant();
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

    public static long secondsUntilOpen(Instant now) {
        if (!isHudVisible(now) || isOpen(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, openInstant(now)).getSeconds());
    }

    public static Instant sessionEnd(Instant start, Duration duration) {
        Instant naturalEnd = start.plus(duration);
        Instant dailyClose = closeInstant(start);
        return naturalEnd.isAfter(dailyClose) ? dailyClose : naturalEnd;
    }

    private NetherSchedulePolicy() {
    }
}
