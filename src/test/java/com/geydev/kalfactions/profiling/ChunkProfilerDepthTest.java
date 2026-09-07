package com.geydev.kalfactions.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChunkProfilerDepthTest {
    @BeforeEach
    void reset() {
        ChunkProfiler.resetDepth();
    }

    @Test
    void aSingleTickIsTimedFromStartToFinish() {
        assertTrue(ChunkProfiler.enter());
        assertTrue(ChunkProfiler.exit());
        assertEquals(0, ChunkProfiler.depth());
    }

    @Test
    void aNestedTickDoesNotEndTheOuterMeasurement() {
        assertTrue(ChunkProfiler.enter());
        assertFalse(ChunkProfiler.enter());

        assertFalse(ChunkProfiler.exit());
        assertEquals(1, ChunkProfiler.depth());

        assertTrue(ChunkProfiler.exit());
        assertEquals(0, ChunkProfiler.depth());
    }

    @Test
    void deepNestingReportsOnlyOnce() {
        assertTrue(ChunkProfiler.enter());
        for (int level = 0; level < 8; level++) {
            assertFalse(ChunkProfiler.enter());
        }
        for (int level = 0; level < 8; level++) {
            assertFalse(ChunkProfiler.exit());
        }

        assertTrue(ChunkProfiler.exit());
        assertEquals(0, ChunkProfiler.depth());
    }

    @Test
    void anUnbalancedEndNeverDrivesTheDepthNegative() {
        assertFalse(ChunkProfiler.exit());
        assertEquals(0, ChunkProfiler.depth());

        assertTrue(ChunkProfiler.enter());
        assertTrue(ChunkProfiler.exit());
        assertFalse(ChunkProfiler.exit());
        assertEquals(0, ChunkProfiler.depth());
    }

    @Test
    void resettingBetweenTicksDropsAnAbandonedMeasurement() {
        assertTrue(ChunkProfiler.enter());
        assertFalse(ChunkProfiler.enter());

        ChunkProfiler.resetDepth();

        assertEquals(0, ChunkProfiler.depth());
        assertTrue(ChunkProfiler.enter());
    }
}
