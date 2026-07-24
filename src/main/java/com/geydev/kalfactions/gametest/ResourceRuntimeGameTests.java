package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager.ChunkDiagnostic;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResourceRuntimeGameTests {
    @GameTest(template = "empty", batch = "resource_runtime", timeoutTicks = 200)
    public static void runtimePlacesDepositsAndSurfaceClustersByZone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        manager.setResourceQueuePaused(false);
        manager.advanceResourceCycle(System.currentTimeMillis());

        ChunkPos blue = new ChunkPos(level.getSharedSpawnPos());
        manager.queue(blue, level.getGameTime() - 1L);
        manager.tick(level);
        ChunkDiagnostic blueDiagnostic = manager.diagnoseChunk(level, blue);
        helper.assertValueEqual(blueDiagnostic.zone(), ResourceZone.BLUE, "blue zone");
        helper.assertTrue(blueDiagnostic.depositCandidateChunk() == null, "blue deposit disabled");
        helper.assertTrue(blueDiagnostic.surfaceCandidateChunk() == null, "blue surface cluster disabled");

        ChunkPos yellowDeposit = findDepositCandidate(level, manager, ResourceZone.YELLOW);
        ChunkPos redDeposit = findDepositCandidate(level, manager, ResourceZone.RED);
        ChunkPos blackDeposit = findDepositCandidate(level, manager, ResourceZone.BLACK);
        generateDeposit(helper, manager, yellowDeposit, ResourceZone.YELLOW, 0.5D);
        generateDeposit(helper, manager, redDeposit, ResourceZone.RED, 1.1D);
        generateDeposit(helper, manager, blackDeposit, ResourceZone.BLACK, 1.7D);

        ChunkDiagnostic blackDiagnostic = manager.diagnoseChunk(level, blackDeposit);
        helper.assertTrue(blackDiagnostic.surfaceCandidateChunk() == null, "black surface cluster disabled");

        generateSurfaceCluster(helper, manager, findSurfaceCandidate(level, manager, ResourceZone.YELLOW));
        generateSurfaceCluster(helper, manager, findSurfaceCandidate(level, manager, ResourceZone.RED));
        helper.succeed();
    }

    private static void generateDeposit(
            GameTestHelper helper,
            ResourceClusterManager manager,
            ChunkPos chunk,
            ResourceZone expectedZone,
            double expectedMultiplier
    ) {
        ServerLevel level = helper.getLevel();
        level.getChunk(chunk.x, chunk.z);
        ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, chunk);
        helper.assertTrue(
                diagnostic.depositReason().equals("pending") || diagnostic.depositReason().startsWith("deposit_"),
                expectedZone + " load event queued or deposit restored"
        );
        helper.assertValueEqual(diagnostic.zone(), expectedZone, expectedZone + " candidate zone");
        helper.assertValueEqual(diagnostic.densityMultiplier(), expectedMultiplier, expectedZone + " density profile");
        helper.assertTrue(diagnostic.depositCenter() != null, expectedZone + " deposit center");
        BlockPos center = diagnostic.depositCenter();
        int minY = Math.max(level.getMinBuildHeight() + 1, center.getY() - 6);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, center.getY() + 6);
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        manager.queue(chunk, level.getGameTime() - 1L);
        for (int tick = 0; tick < 16; tick++) {
            manager.tick(level);
        }
        ResourceClusterManager.OreDepositView deposit = manager.oreDepositAt(chunk).orElse(null);
        helper.assertTrue(deposit != null, expectedZone + " deposit created");
        helper.assertTrue(deposit.generatedBlocks() > 0, expectedZone + " generated tracked blocks");
        helper.assertTrue(manager.physicalOreBlocksInChunk(level, chunk) > 0, expectedZone + " physical ore blocks");
    }

    private static void generateSurfaceCluster(
            GameTestHelper helper,
            ResourceClusterManager manager,
            ChunkPos chunk
    ) {
        ServerLevel level = helper.getLevel();
        level.getChunk(chunk.x, chunk.z);
        ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, chunk);
        helper.assertTrue(diagnostic.surfacePosition() != null, diagnostic.zone() + " surface position");
        manager.queue(chunk, level.getGameTime() - 1L);
        manager.tick(level);
        ResourceClusterManager.ClusterView cluster = manager.clusterAt(chunk).orElse(null);
        helper.assertTrue(cluster != null, diagnostic.zone() + " surface cluster created");
        helper.assertTrue(
                level.getBlockState(cluster.basePos()).is(cluster.type().block()),
                diagnostic.zone() + " surface block placed"
        );
    }

    private static ChunkPos findDepositCandidate(
            ServerLevel level,
            ResourceClusterManager manager,
            ResourceZone zone
    ) {
        BlockPos spawn = level.getSharedSpawnPos();
        int minDistance = switch (zone) {
            case YELLOW -> 512;
            case RED -> 5_250;
            case BLACK -> 8_250;
            case BLUE -> 0;
        };
        int maxDistance = switch (zone) {
            case YELLOW -> 4_750;
            case RED -> 7_750;
            case BLACK -> 9_500;
            case BLUE -> 200;
        };
        for (int x = minDistance; x <= maxDistance; x += 256) {
            for (int z = -768; z <= 768; z += 256) {
                ChunkPos query = new ChunkPos(new BlockPos(spawn.getX() + x, 0, spawn.getZ() + z));
                ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, query);
                if (diagnostic.zone() != zone || diagnostic.depositCandidateChunk() == null) {
                    continue;
                }
                ChunkPos candidate = diagnostic.depositCandidateChunk();
                ChunkDiagnostic candidateDiagnostic = manager.diagnoseChunk(level, candidate);
                if (candidateDiagnostic.zone() == zone
                        && level.getWorldBorder().isWithinBounds(new BlockPos(
                                candidate.getMiddleBlockX(),
                                0,
                                candidate.getMiddleBlockZ()
                        ))) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("No " + zone + " deposit candidate found");
    }

    private static ChunkPos findSurfaceCandidate(
            ServerLevel level,
            ResourceClusterManager manager,
            ResourceZone zone
    ) {
        BlockPos spawn = level.getSharedSpawnPos();
        int distance = zone == ResourceZone.YELLOW ? 768 : 5_500;
        ChunkPos origin = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ()));
        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                ChunkPos candidate = new ChunkPos(origin.x + dx, origin.z + dz);
                ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, candidate);
                if (diagnostic.zone() == zone && diagnostic.surfaceCandidateChunk() != null) {
                    ChunkPos actual = diagnostic.surfaceCandidateChunk();
                    if (manager.diagnoseChunk(level, actual).zone() == zone) {
                        return actual;
                    }
                }
            }
        }
        throw new IllegalStateException("No " + zone + " surface candidate found");
    }

    private ResourceRuntimeGameTests() {
    }
}
