package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class TraderPricingTest {
    @Test
    void sellBonusesComposeWithCeiling() {
        assertEquals(100L, TraderService.sellUnitPrice(100L, 0, false, 0.25D));
        assertEquals(110L, TraderService.sellUnitPrice(100L, 1, false, 0.25D));
        assertEquals(138L, TraderService.sellUnitPrice(100L, 1, true, 0.25D));
    }

    @Test
    void buyDiscountIsBoundedAtNinetyPercent() {
        assertEquals(100L, TraderService.buyUnitPrice(100L, 0));
        assertEquals(70L, TraderService.buyUnitPrice(100L, 3));
        assertEquals(10L, TraderService.buyUnitPrice(100L, 50));
    }
}
