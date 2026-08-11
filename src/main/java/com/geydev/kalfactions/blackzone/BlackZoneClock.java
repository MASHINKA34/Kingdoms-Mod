package com.geydev.kalfactions.blackzone;

import java.util.function.LongSupplier;

public final class BlackZoneClock {
    private static final LongSupplier SYSTEM = System::currentTimeMillis;

    private static volatile LongSupplier source = SYSTEM;

    public static long now() {
        return source.getAsLong();
    }

    public static void override(LongSupplier replacement) {
        source = replacement == null ? SYSTEM : replacement;
    }

    public static void reset() {
        source = SYSTEM;
    }

    private BlackZoneClock() {
    }
}
