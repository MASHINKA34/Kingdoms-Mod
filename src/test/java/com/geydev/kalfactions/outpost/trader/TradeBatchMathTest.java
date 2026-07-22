package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TradeBatchMathTest {
    @Test
    void batchSizeControlsAvailabilityItemsAndPayout() {
        assertEquals(3, TradeBatchMath.availableBatches(7, 2, 10));
        assertEquals(2, TradeBatchMath.availableBatches(20, 4, 2));
        assertEquals(12, TradeBatchMath.itemsForBatches(3, 4));
        assertEquals(75L, TradeBatchMath.payout(25L, 3));
    }

    @Test
    void invalidAndOverflowingInputsAreBounded() {
        assertEquals(0, TradeBatchMath.availableBatches(1, 2, 10));
        assertEquals(0, TradeBatchMath.itemsForBatches(-1, 2));
        assertEquals(Integer.MAX_VALUE, TradeBatchMath.itemsForBatches(Integer.MAX_VALUE, 64));
        assertEquals(Long.MAX_VALUE, TradeBatchMath.payout(Long.MAX_VALUE, 2));
    }
}
