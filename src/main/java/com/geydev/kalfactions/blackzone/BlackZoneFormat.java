package com.geydev.kalfactions.blackzone;

import java.util.Locale;

public final class BlackZoneFormat {
    public static String clock(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        return String.format(
                Locale.ROOT,
                "%d:%02d:%02d",
                totalSeconds / 3600L,
                (totalSeconds % 3600L) / 60L,
                totalSeconds % 60L
        );
    }

    private BlackZoneFormat() {
    }
}
