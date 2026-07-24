package com.geydev.kalfactions.quarry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuarryDistributionTest {
    @Test
    void candidateCentersAreMoreThanTenChunksApart() {
        List<Point> candidates = collect(918_273L);
        assertTrue(candidates.size() > 10);
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                Point a = candidates.get(first);
                Point b = candidates.get(second);
                int distance = Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
                assertTrue(distance > QuarryManager.MINIMUM_SPACING_CHUNKS);
            }
        }
    }

    @Test
    void placementIsDeterministicForTheWorldSeed() {
        assertEquals(collect(123_456L), collect(123_456L));
    }

    private static List<Point> collect(long seed) {
        List<Point> result = new ArrayList<>();
        for (int x = -80; x <= 80; x++) {
            for (int z = -80; z <= 80; z++) {
                if (QuarryDistribution.isCandidate(
                        seed,
                        x,
                        z,
                        QuarryManager.MINIMUM_SPACING_CHUNKS
                )) {
                    result.add(new Point(x, z));
                }
            }
        }
        return result;
    }

    private record Point(int x, int z) {
    }
}
