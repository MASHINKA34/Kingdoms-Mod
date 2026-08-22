package com.geydev.kalfactions.dimension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import net.minecraft.network.chat.Component;

public final class NetherSchedulePolicy {
    public static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    public static final LocalTime OPENS_AT = LocalTime.of(18, 0);
    public static final LocalTime CLOSES_AT = LocalTime.of(23, 0);

    public static boolean isOpen(Instant now) {
        LocalTime time = now.atZone(MOSCOW).toLocalTime();
        return !time.isBefore(OPENS_AT) && time.isBefore(CLOSES_AT);
    }

    public static boolean canStartSession(Instant now) {
        return isOpen(now);
    }

    public static Instant nextOpenInstant(Instant now) {
        ZonedDateTime moscow = now.atZone(MOSCOW);
        ZonedDateTime today = ZonedDateTime.of(moscow.toLocalDate(), OPENS_AT, MOSCOW);
        return now.isBefore(today.toInstant()) ? today.toInstant() : today.plusDays(1).toInstant();
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

    public static String formatClock(Duration remaining) {
        long seconds = Math.max(0L, remaining.getSeconds()) % 86_400L;
        return String.format(
                Locale.ROOT, "%02d:%02d:%02d", seconds / 3600L, (seconds % 3600L) / 60L, seconds % 60L
        );
    }

    public static Component formatRemaining(Duration remaining) {
        long days = Math.max(0L, remaining.getSeconds()) / 86_400L;
        String clock = formatClock(remaining);
        return days > 0L
                ? Component.translatable("kingdoms.time.days_clock", days, clock)
                : Component.literal(clock);
    }

    public static Instant sessionEnd(Instant start, Duration duration) {
        Instant naturalEnd = start.plus(duration);
        Instant dailyClose = closeInstant(start);
        return naturalEnd.isAfter(dailyClose) ? dailyClose : naturalEnd;
    }

    private NetherSchedulePolicy() {
    }
}
