package com.geydev.kalfactions.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QuarryStructurePlacerTest {
    @Test
    void keepsFlatTerrainAtItsSurface() {
        int[] samples = new int[49];
        Arrays.fill(samples, 90);

        assertEquals(90, QuarryStructurePlacer.adjustedSurfaceY(90, samples));
    }

    @Test
    void followsTheLowerPartOfSlopedTerrain() {
        int[] samples = new int[49];
        Arrays.fill(samples, 86);
        Arrays.fill(samples, 0, 20, 82);

        assertEquals(82, QuarryStructurePlacer.adjustedSurfaceY(86, samples));
    }

    @Test
    void limitsLoweringAcrossDeepTerrainDrops() {
        int[] samples = new int[49];
        Arrays.fill(samples, 70);

        assertEquals(94, QuarryStructurePlacer.adjustedSurfaceY(100, samples));
    }

    @Test
    void ignoresIsolatedRavineSamples() {
        int[] samples = new int[49];
        Arrays.fill(samples, 90);
        Arrays.fill(samples, 0, 8, 50);

        assertEquals(90, QuarryStructurePlacer.adjustedSurfaceY(90, samples));
    }
}
