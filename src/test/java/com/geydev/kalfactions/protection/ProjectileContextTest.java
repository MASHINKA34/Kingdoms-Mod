package com.geydev.kalfactions.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectileContextTest {
    @BeforeEach
    @AfterEach
    void clear() {
        MachineProtection.clearProjectileContext();
    }

    @Test
    void aBalancedTickLeavesNoOpenContext() {
        MachineProtection.beginProjectileContext(null, 100L);
        assertEquals(1, MachineProtection.openProjectileContexts());

        MachineProtection.endProjectileContext();

        assertEquals(0, MachineProtection.openProjectileContexts());
    }

    @Test
    void nestedTicksUnwindToZero() {
        for (int depth = 1; depth <= 4; depth++) {
            MachineProtection.beginProjectileContext(null, 100L);
            assertEquals(depth, MachineProtection.openProjectileContexts());
        }
        for (int depth = 3; depth >= 0; depth--) {
            MachineProtection.endProjectileContext();
            assertEquals(depth, MachineProtection.openProjectileContexts());
        }
    }

    @Test
    void anUnbalancedEndNeverDrivesTheCountNegative() {
        MachineProtection.endProjectileContext();
        assertEquals(0, MachineProtection.openProjectileContexts());

        MachineProtection.beginProjectileContext(null, 100L);
        MachineProtection.endProjectileContext();
        MachineProtection.endProjectileContext();

        assertEquals(0, MachineProtection.openProjectileContexts());
    }

    @Test
    void theEndOfTickResetClosesALeakedContext() {
        MachineProtection.beginProjectileContext(null, 100L);
        MachineProtection.beginProjectileContext(null, 100L);

        MachineProtection.clearProjectileContext();

        assertEquals(0, MachineProtection.openProjectileContexts());
    }

    @Test
    void aContextLeakedIntoTheNextTickIsReplacedNotStacked() {
        MachineProtection.beginProjectileContext(null, 100L);
        MachineProtection.beginProjectileContext(null, 100L);

        MachineProtection.beginProjectileContext(null, 101L);

        assertEquals(1, MachineProtection.openProjectileContexts());

        MachineProtection.endProjectileContext();

        assertEquals(0, MachineProtection.openProjectileContexts());
    }
}
