package com.geydev.kalfactions.tax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TaxMathTest {
    private static final double TIER1_LIMIT = 2.0D;
    private static final double TIER2_LIMIT = 5.0D;
    private static final long PRICE1 = 100L;
    private static final long PRICE2 = 250L;
    private static final long PRICE3 = 600L;
    private static final long DAY_TICKS = 24_000L;

    private static double costPerDay(double excessMs) {
        return TaxMath.costPerDay(excessMs, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3);
    }

    @Test
    void noExcessCostsNothing() {
        assertEquals(0.0D, costPerDay(0.0D));
        assertEquals(0.0D, costPerDay(-1.0D));
    }

    @Test
    void tierOneOnly() {
        assertEquals(1_000.0D, costPerDay(1.0D), 1e-9);
        assertEquals(2_000.0D, costPerDay(2.0D), 1e-9);
        assertEquals(100.0D, costPerDay(0.1D), 1e-9);
    }

    @Test
    void tierTwoStacksOnTierOne() {
        assertEquals(2_000.0D + 2_500.0D, costPerDay(3.0D), 1e-9);
        assertEquals(2_000.0D + 7_500.0D, costPerDay(5.0D), 1e-9);
    }

    @Test
    void tierThreeStacksOnLowerTiers() {
        assertEquals(2_000.0D + 7_500.0D + 6_000.0D, costPerDay(6.0D), 1e-9);
        assertEquals(2_000.0D + 7_500.0D + 30_000.0D, costPerDay(10.0D), 1e-9);
    }

    @Test
    void accrualIsProportionalToTickedTime() {
        long fullDay = TaxMath.accrualMicros(
                5.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, DAY_TICKS, DAY_TICKS);
        assertEquals(4_500L * TaxMath.MICROS, fullDay);

        long halfDay = TaxMath.accrualMicros(
                5.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, DAY_TICKS / 2L, DAY_TICKS);
        assertEquals(fullDay / 2L, halfDay);

        long accumulated = 0L;
        for (int sample = 0; sample < DAY_TICKS / 4L; sample++) {
            accumulated += TaxMath.accrualMicros(
                    5.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, 4L, DAY_TICKS);
        }
        assertEquals(4_500L, TaxMath.billFromMicros(accumulated));
    }

    @Test
    void quotaIsSubtractedBeforeTariff() {
        assertEquals(0L, TaxMath.accrualMicros(
                2.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, DAY_TICKS, DAY_TICKS));
        long oneMsOver = TaxMath.accrualMicros(
                3.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, DAY_TICKS, DAY_TICKS);
        assertEquals(1_000L * TaxMath.MICROS, oneMsOver);
    }

    @Test
    void idleFactoriesPayNothing() {
        assertEquals(0L, TaxMath.accrualMicros(
                0.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, DAY_TICKS, DAY_TICKS));
        assertEquals(0L, TaxMath.accrualMicros(
                10.0D, 2.0D, TIER1_LIMIT, TIER2_LIMIT, PRICE1, PRICE2, PRICE3, 0L, DAY_TICKS));
    }

    @Test
    void billRoundsHalfUp() {
        assertEquals(0L, TaxMath.billFromMicros(0L));
        assertEquals(0L, TaxMath.billFromMicros(499_999L));
        assertEquals(1L, TaxMath.billFromMicros(500_000L));
        assertEquals(2L, TaxMath.billFromMicros(1_500_000L));
    }

    @Test
    void averageMsRecoversConstantLoad() {
        long integral = Math.round(3.5D * TaxMath.MICROS * DAY_TICKS);
        assertEquals(3.5D, TaxMath.averageMs(integral, DAY_TICKS), 1e-6);
        assertEquals(0.0D, TaxMath.averageMs(0L, DAY_TICKS));
        assertEquals(0.0D, TaxMath.averageMs(integral, 0L));
    }

    @Test
    void emaAlphaMatchesWindow() {
        double alpha = TaxMath.emaAlpha(4, 300);
        assertEquals(4.0D / 6_000.0D, alpha, 1e-12);
        assertTrue(TaxMath.emaAlpha(20, 1) <= 1.0D);
    }
}
