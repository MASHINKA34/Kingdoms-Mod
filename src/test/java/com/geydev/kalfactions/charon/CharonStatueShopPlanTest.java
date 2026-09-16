package com.geydev.kalfactions.charon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CharonStatueShopPlanTest {
    @Test
    void coinsAloneCoverThePrice() {
        assertEquals(new CharonStatueShop.Split(800L, 0L, 0L), CharonStatueShop.plan(800L, 1000L, 500L, 500L));
    }

    @Test
    void theBankCoversWhatTheCoinsDoNot() {
        assertEquals(new CharonStatueShop.Split(300L, 500L, 0L), CharonStatueShop.plan(800L, 300L, 700L, 400L));
    }

    @Test
    void theTreasuryCoversTheRest() {
        assertEquals(new CharonStatueShop.Split(300L, 200L, 300L), CharonStatueShop.plan(800L, 300L, 200L, 300L));
    }

    @Test
    void exactlyEnoughIsAccepted() {
        assertEquals(new CharonStatueShop.Split(1L, 1L, 1L), CharonStatueShop.plan(3L, 1L, 1L, 1L));
    }

    @Test
    void missingASingleSpurIsRefused() {
        assertNull(CharonStatueShop.plan(800L, 300L, 200L, 299L));
        assertNull(CharonStatueShop.plan(1L, 0L, 0L, 0L));
    }

    @Test
    void aFreePriceTakesNothing() {
        assertEquals(new CharonStatueShop.Split(0L, 0L, 0L), CharonStatueShop.plan(0L, 900L, 900L, 900L));
    }

    @Test
    void unavailableSourcesAreNotUsed() {
        assertEquals(new CharonStatueShop.Split(300L, 0L, 500L), CharonStatueShop.plan(800L, 300L, 0L, 900L));
        assertEquals(new CharonStatueShop.Split(300L, 500L, 0L), CharonStatueShop.plan(800L, 300L, 900L, 0L));
        assertNull(CharonStatueShop.plan(800L, 300L, 0L, 0L));
    }

    @Test
    void negativeBalancesCountAsEmpty() {
        assertEquals(new CharonStatueShop.Split(0L, 100L, 0L), CharonStatueShop.plan(100L, -50L, 100L, 0L));
        assertNull(CharonStatueShop.plan(100L, -50L, -50L, -50L));
    }
}
