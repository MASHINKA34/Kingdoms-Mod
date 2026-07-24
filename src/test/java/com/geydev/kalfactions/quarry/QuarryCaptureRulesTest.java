package com.geydev.kalfactions.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QuarryCaptureRulesTest {
    private static final int FULL = 6_000;

    @Test
    void leavingResetsTheWholeCaptureTimer() {
        QuarryCaptureRules.TickResult result =
                QuarryCaptureRules.tick(false, false, 1_200, 20, FULL);

        assertEquals(QuarryCaptureRules.Action.RESET, result.action());
        assertEquals(FULL, result.remainingTicks());
    }

    @Test
    void defenderPresencePausesWithoutResettingProgress() {
        QuarryCaptureRules.TickResult result =
                QuarryCaptureRules.tick(true, true, 1_200, 20, FULL);

        assertEquals(QuarryCaptureRules.Action.PAUSED, result.action());
        assertEquals(1_200, result.remainingTicks());
    }

    @Test
    void undefendedAttackCountsDownAndCompletes() {
        QuarryCaptureRules.TickResult counting =
                QuarryCaptureRules.tick(true, false, 1_200, 20, FULL);
        QuarryCaptureRules.TickResult captured =
                QuarryCaptureRules.tick(true, false, 20, 20, FULL);

        assertEquals(QuarryCaptureRules.Action.COUNTING, counting.action());
        assertEquals(1_180, counting.remainingTicks());
        assertEquals(QuarryCaptureRules.Action.CAPTURED, captured.action());
        assertEquals(0, captured.remainingTicks());
    }
}
