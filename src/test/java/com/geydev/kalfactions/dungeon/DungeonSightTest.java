package com.geydev.kalfactions.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DungeonSightTest {
    @Test
    void theConfiguredPercentsMapToTheirAmplifiers() {
        assertEquals(2, DungeonSight.amplifierFor(35));
        assertEquals(6, DungeonSight.amplifierFor(70));
        assertEquals(9, DungeonSight.amplifierFor(100));
    }

    @Test
    void theAmplifiersMapBackToTheirBrightness() {
        assertEquals(0.3F, DungeonSight.scaleFor(DungeonSight.amplifierFor(35)), 1.0E-6F);
        assertEquals(0.7F, DungeonSight.scaleFor(DungeonSight.amplifierFor(70)), 1.0E-6F);
        assertEquals(1.0F, DungeonSight.scaleFor(DungeonSight.amplifierFor(100)), 1.0E-6F);
    }

    @Test
    void percentsOutsideTheConfiguredRangeAreClamped() {
        assertEquals(DungeonSight.amplifierFor(10), DungeonSight.amplifierFor(0));
        assertEquals(DungeonSight.amplifierFor(10), DungeonSight.amplifierFor(-40));
        assertEquals(DungeonSight.amplifierFor(100), DungeonSight.amplifierFor(140));
        assertEquals(0, DungeonSight.amplifierFor(10));
        assertEquals(0.1F, DungeonSight.scaleFor(DungeonSight.amplifierFor(10)), 1.0E-6F);
    }

    @Test
    void amplifiersOutsideTheEffectRangeAreClamped() {
        assertEquals(0.1F, DungeonSight.scaleFor(-3), 1.0E-6F);
        assertEquals(1.0F, DungeonSight.scaleFor(40), 1.0E-6F);
    }

    @Test
    void theDefaultRulesCarryTheThreeSteps() {
        DungeonSightRules rules = DungeonSightRules.DEFAULT;

        assertEquals(0, rules.percentFor(0));
        assertEquals(35, rules.percentFor(1));
        assertEquals(70, rules.percentFor(2));
        assertEquals(100, rules.percentFor(3));
        assertEquals(0, rules.percentFor(DungeonManager.MAX_LIGHTING + 1));
    }

    @Test
    void ruleOverridesFallBackToTheDefaultsOnReset() {
        try {
            DungeonSightRules.override(new DungeonSightRules(false, new int[] {10, 10, 10}, 40));

            assertEquals(false, DungeonSightRules.configured().enabled());
            assertEquals(10, DungeonSightRules.configured().percentFor(3));
        } finally {
            DungeonSightRules.reset();
        }

        assertEquals(DungeonSightRules.DEFAULT.effectTicks(), DungeonSightRules.configured().effectTicks());
        assertEquals(100, DungeonSightRules.configured().percentFor(3));
    }

    @Test
    void ruleBrightnessIsClampedToTheConfiguredRange() {
        DungeonSightRules rules = new DungeonSightRules(true, new int[] {0, 70, 900}, 300);

        assertEquals(DungeonSight.MIN_PERCENT, rules.percentFor(1));
        assertEquals(70, rules.percentFor(2));
        assertEquals(DungeonSight.MAX_PERCENT, rules.percentFor(3));
    }
}
