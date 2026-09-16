package com.geydev.kalfactions.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class WarpScrollRulesTest {
    @AfterEach
    void clearOverride() {
        WarpScrollRules.reset();
    }

    @Test
    void configuredSecondsBecomeTicks() {
        WarpScrollRules rules = WarpScrollRules.of(4, true, 0.2D, true, 3, 7);

        assertEquals(80, rules.castTicks());
        assertEquals(60, rules.damageCooldownTicks());
        assertEquals(140, rules.cancelCooldownTicks());
        assertEquals(4, rules.castSeconds());
        assertEquals(3, rules.damageCooldownSeconds());
    }

    @Test
    void theMoveToleranceIsStoredSquared() {
        assertEquals(0.04D, WarpScrollRules.of(4, true, 0.2D, true, 3, 0).moveToleranceSquared(), 1.0E-9D);
        assertEquals(0.0D, WarpScrollRules.of(4, true, 0.0D, true, 3, 0).moveToleranceSquared(), 1.0E-9D);
        assertEquals(16.0D, WarpScrollRules.of(4, true, 4.0D, true, 3, 0).moveToleranceSquared(), 1.0E-9D);
    }

    @Test
    void zeroCastSecondsMeanNoChannel() {
        WarpScrollRules rules = WarpScrollRules.of(0, true, 0.2D, true, 3, 0);

        assertEquals(0, rules.castTicks());
        assertEquals(0, rules.castSeconds());
    }

    @Test
    void theFlagsSurviveTheConversion() {
        WarpScrollRules rules = WarpScrollRules.of(4, false, 0.2D, false, 3, 0);

        assertFalse(rules.cancelOnMove());
        assertFalse(rules.cancelOnDamage());
        assertTrue(WarpScrollRules.DEFAULT.cancelOnMove());
        assertTrue(WarpScrollRules.DEFAULT.cancelOnDamage());
    }

    @Test
    void negativeTicksAreRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpScrollRules(-1, true, 0.04D, true, 60, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpScrollRules(80, true, -0.04D, true, 60, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpScrollRules(80, true, 0.04D, true, -1, 0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WarpScrollRules(80, true, 0.04D, true, 60, -1)
        );
    }

    @Test
    void anOverrideReplacesTheConfiguredRules() {
        WarpScrollRules rules = WarpScrollRules.of(1, true, 1.0D, true, 2, 0);

        WarpScrollRules.override(rules);

        assertSame(rules, WarpScrollRules.configured());
    }

    @Test
    void theCooldownFollowsTheCancellationReason() {
        WarpScrollRules rules = WarpScrollRules.of(4, true, 0.2D, true, 3, 1);

        assertEquals(60, WarpChannelEvents.cooldownTicks(rules, WarpChannelEvents.Reason.DAMAGED));
        assertEquals(20, WarpChannelEvents.cooldownTicks(rules, WarpChannelEvents.Reason.MOVED));
        assertEquals(20, WarpChannelEvents.cooldownTicks(rules, WarpChannelEvents.Reason.RELEASED));
        assertEquals(0, WarpChannelEvents.cooldownTicks(rules, WarpChannelEvents.Reason.INVALID));
        assertEquals(
                0,
                WarpChannelEvents.cooldownTicks(
                        WarpScrollRules.of(4, true, 0.2D, true, 0, 0),
                        WarpChannelEvents.Reason.DAMAGED
                )
        );
    }
}
