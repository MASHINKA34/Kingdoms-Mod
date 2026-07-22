package com.geydev.kalfactions.outpost.cluster.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResourceDistributionTest {
    @Test
    void classifiesZonesAroundActualSpawn() {
        int spawnX = 1_375;
        int spawnZ = -820;
        ResourceDistribution distribution = new ResourceDistribution(
                42L,
                0L,
                spawnX,
                spawnZ,
                ResourceDistributionConfig.defaults()
        );

        assertEquals(ResourceZone.BLUE, distribution.zoneAt(spawnX + 5_000, spawnZ));
        assertEquals(ResourceZone.YELLOW, distribution.zoneAt(spawnX + 5_001, spawnZ));
        assertEquals(ResourceZone.YELLOW, distribution.zoneAt(spawnX, spawnZ - 8_000));
        assertEquals(ResourceZone.BLACK, distribution.zoneAt(spawnX, spawnZ - 8_001));
    }

    @Test
    void sameSeedAndCycleProduceIdenticalCandidates() {
        ResourceDistribution first = distribution(918_273_645L, 7L);
        ResourceDistribution second = distribution(918_273_645L, 7L);

        for (int x = -80; x <= 80; x++) {
            for (int z = -80; z <= 80; z++) {
                assertEquals(first.candidateForCell(x, z), second.candidateForCell(x, z));
            }
        }
    }

    @Test
    void cycleChangesDeterministicLayout() {
        ResourceDistribution current = distribution(123_456_789L, 3L);
        ResourceDistribution next = current.withCycleId(4L);
        int differences = 0;
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                if (!current.candidateForCell(x, z).equals(next.candidateForCell(x, z))) {
                    differences++;
                }
            }
        }

        assertTrue(differences > 5_000);
        assertNotEquals(current.cycleId(), next.cycleId());
    }

    @Test
    void densityRichnessAndRareShareGrowOutward() {
        ResourceDistribution distribution = distribution(7_654_321L, 2L);
        ZoneStats blue = collect(distribution, ResourceZone.BLUE, -15, 15, -15, 15);
        ZoneStats yellow = collect(distribution, ResourceZone.YELLOW, 21, 30, -15, 15);
        ZoneStats black = collect(distribution, ResourceZone.BLACK, 35, 65, -15, 15);

        assertTrue(blue.density() < yellow.density());
        assertTrue(yellow.density() < black.density());
        assertTrue(blue.averageReserve() < yellow.averageReserve());
        assertTrue(yellow.averageReserve() < black.averageReserve());
        assertTrue(blue.averageSize() < yellow.averageSize());
        assertTrue(yellow.averageSize() < black.averageSize());
        assertTrue(blue.rareShare() < yellow.rareShare());
        assertTrue(yellow.rareShare() < black.rareShare());
    }

    @Test
    void candidateAlwaysStaysInsideItsCellAndZone() {
        ResourceDistribution distribution = distribution(88L, 9L);
        int cellSize = distribution.config().cellSize();
        for (int x = -50; x <= 50; x++) {
            for (int z = -50; z <= 50; z++) {
                int cellX = x;
                int cellZ = z;
                distribution.candidateForCell(cellX, cellZ).ifPresent(candidate -> {
                    assertTrue(candidate.blockX() >= cellX * cellSize);
                    assertTrue(candidate.blockX() < (cellX + 1) * cellSize);
                    assertTrue(candidate.blockZ() >= cellZ * cellSize);
                    assertTrue(candidate.blockZ() < (cellZ + 1) * cellSize);
                    assertEquals(candidate.zone(), distribution.zoneAt(candidate.blockX(), candidate.blockZ()));
                });
            }
        }
    }

    private static ResourceDistribution distribution(long seed, long cycle) {
        return new ResourceDistribution(seed, cycle, 0, 0, ResourceDistributionConfig.defaults());
    }

    private static ZoneStats collect(
            ResourceDistribution distribution,
            ResourceZone expected,
            int minCellX,
            int maxCellX,
            int minCellZ,
            int maxCellZ
    ) {
        int eligible = 0;
        List<ResourceDistribution.CellCandidate> candidates = new ArrayList<>();
        for (int x = minCellX; x <= maxCellX; x++) {
            for (int z = minCellZ; z <= maxCellZ; z++) {
                double centerX = x * distribution.config().cellSize() + distribution.config().cellSize() / 2.0D;
                double centerZ = z * distribution.config().cellSize() + distribution.config().cellSize() / 2.0D;
                if (distribution.zoneAt(centerX, centerZ) != expected) {
                    continue;
                }
                eligible++;
                distribution.candidateForCell(x, z).ifPresent(candidates::add);
            }
        }
        long reserve = candidates.stream().mapToLong(ResourceDistribution.CellCandidate::reserve).sum();
        long size = candidates.stream().mapToLong(ResourceDistribution.CellCandidate::size).sum();
        long rare = candidates.stream().filter(candidate -> candidate.resource().isRare()).count();
        return new ZoneStats(
                candidates.size() / (double) eligible,
                reserve / (double) candidates.size(),
                size / (double) candidates.size(),
                rare / (double) candidates.size()
        );
    }

    private record ZoneStats(double density, double averageReserve, double averageSize, double rareShare) {
    }
}
