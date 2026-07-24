package com.geydev.kalfactions.outpost.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SurfaceClusterDistributionTest {
    @Test
    void blueAndBlackZonesNeverProduceSurfaceClusters() {
        SurfaceClusterDistribution distribution = distribution();
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                assertTrue(distribution.candidateForChunk(x, z).isEmpty());
            }
        }
        for (int x = 510; x <= 540; x++) {
            for (int z = -20; z <= 20; z++) {
                assertTrue(distribution.candidateForChunk(x, z).isEmpty());
            }
        }
    }

    @Test
    void candidatesRespectZoneSpecificMinimumSpacing() {
        SurfaceClusterDistribution distribution = distribution();
        List<ChunkCandidate> yellow = collect(distribution, 20, 300, -140, 140, ResourceZone.YELLOW);
        List<ChunkCandidate> red = collect(distribution, 320, 490, -140, 140, ResourceZone.RED);
        assertTrue(yellow.size() > 20);
        assertTrue(red.size() > yellow.size());
        assertSpacing(yellow, 9);
        assertSpacing(red, 4);
    }

    @Test
    void layoutIsDeterministic() {
        SurfaceClusterDistribution first = distribution();
        SurfaceClusterDistribution second = distribution();
        for (int x = -500; x <= 500; x += 7) {
            for (int z = -500; z <= 500; z += 7) {
                assertEquals(first.candidateForChunk(x, z), second.candidateForChunk(x, z));
            }
        }
    }

    private static List<ChunkCandidate> collect(
            SurfaceClusterDistribution distribution,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            ResourceZone zone
    ) {
        List<ChunkCandidate> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                SurfaceClusterDistribution.Candidate candidate =
                        distribution.candidateForChunk(x, z).orElse(null);
                if (candidate != null && candidate.zone() == zone) {
                    result.add(new ChunkCandidate(x, z));
                }
            }
        }
        return result;
    }

    private static void assertSpacing(List<ChunkCandidate> candidates, int minimum) {
        for (int first = 0; first < candidates.size(); first++) {
            for (int second = first + 1; second < candidates.size(); second++) {
                ChunkCandidate a = candidates.get(first);
                ChunkCandidate b = candidates.get(second);
                int distance = Math.max(Math.abs(a.x - b.x), Math.abs(a.z - b.z));
                assertTrue(distance > minimum);
            }
        }
    }

    private static SurfaceClusterDistribution distribution() {
        return new SurfaceClusterDistribution(918_273L, 0, 0, 200, 5000, 8000, 9, 4);
    }

    private record ChunkCandidate(int x, int z) {
    }
}
