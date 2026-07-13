package com.geydev.kalfactions.tax;

public final class TaxMath {
    public static final long MICROS = 1_000_000L;

    public static double costPerDay(
        double excessMs,
        double tier1LimitMs,
        double tier2LimitMs,
        long tier1Price,
        long tier2Price,
        long tier3Price
    ) {
        if (!Double.isFinite(excessMs) || excessMs <= 0.0D) {
            return 0.0D;
        }
        double tier1Top = Math.max(0.0D, tier1LimitMs);
        double tier2Top = Math.max(tier1Top, tier2LimitMs);
        double tier1 = Math.min(excessMs, tier1Top);
        double tier2 = Math.min(Math.max(excessMs - tier1Top, 0.0D), tier2Top - tier1Top);
        double tier3 = Math.max(excessMs - tier2Top, 0.0D);
        return tier1 * tier1Price * 10.0D + tier2 * tier2Price * 10.0D + tier3 * tier3Price * 10.0D;
    }

    public static long accrualMicros(
        double loadMs,
        double quotaMs,
        double tier1LimitMs,
        double tier2LimitMs,
        long tier1Price,
        long tier2Price,
        long tier3Price,
        long ticks,
        long dayTicks
    ) {
        if (ticks <= 0L || dayTicks <= 0L) {
            return 0L;
        }
        double excess = Math.max(0.0D, loadMs - Math.max(0.0D, quotaMs));
        double perDay = costPerDay(excess, tier1LimitMs, tier2LimitMs, tier1Price, tier2Price, tier3Price);
        double micros = perDay * MICROS * ticks / dayTicks;
        if (micros >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.round(micros);
    }

    public static long billFromMicros(long accruedMicros) {
        if (accruedMicros <= 0L) {
            return 0L;
        }
        return (accruedMicros + MICROS / 2L) / MICROS;
    }

    public static double averageMs(long msTicksMicros, long ticks) {
        if (ticks <= 0L || msTicksMicros <= 0L) {
            return 0.0D;
        }
        return msTicksMicros / (double) MICROS / ticks;
    }

    public static double emaAlpha(int sampleIntervalTicks, int windowSeconds) {
        double windowTicks = Math.max(1, windowSeconds) * 20.0D;
        return Math.min(1.0D, Math.max(1, sampleIntervalTicks) / windowTicks);
    }

    private TaxMath() {
    }
}
