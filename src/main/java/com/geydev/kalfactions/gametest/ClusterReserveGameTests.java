package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.outpost.cluster.ClusterClock;
import com.geydev.kalfactions.outpost.cluster.ClusterMaintenance;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClusterReserveGameTests {
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    @GameTest(template = "empty", batch = "cluster_reserve", timeoutTicks = 600)
    public static void reserveDrainsExhaustsAndRefillsOnTimer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        ChunkPos chunk = generateCluster(level, manager);
        AtomicLong clock = new AtomicLong(1_700_000_000_000L);
        ClusterClock.override(clock::get);
        try {
            ResourceClusterManager.ClusterView cluster = manager.clusterAt(chunk).orElseThrow();
            BlockPos base = cluster.basePos();
            ResourceClusterManager.ReserveView initial = manager.reserveAt(chunk).orElseThrow();
            helper.assertValueEqual(
                    initial.limit(),
                    ResourceClusterManager.limitFor(cluster.richness()),
                    "limit follows richness"
            );
            helper.assertValueEqual(initial.remaining(), initial.limit(), "fresh cluster is full");
            helper.assertFalse(initial.timerRunning(), "fresh cluster has no timer");

            helper.assertValueEqual(manager.consume(level, chunk, 10), 10, "drill batch granted");
            helper.assertValueEqual(manager.consumeMinedBlock(level, base), 1, "mined block granted");
            ResourceClusterManager.ReserveView spent = manager.reserveAt(chunk).orElseThrow();
            helper.assertValueEqual(spent.remaining(), initial.limit() - 11, "reserve dropped by both paths");
            helper.assertTrue(spent.timerRunning(), "first extraction started the timer");
            helper.assertTrue(
                    spent.restoreInMillis() > 0L && spent.restoreInMillis() <= resetMillis(),
                    "restore countdown started"
            );

            int rest = spent.remaining();
            helper.assertValueEqual(manager.consume(level, chunk, rest + 64), rest, "partial last batch");
            ResourceClusterManager.ReserveView empty = manager.reserveAt(chunk).orElseThrow();
            helper.assertValueEqual(empty.remaining(), 0, "cluster is empty");
            helper.assertTrue(empty.exhausted(), "cluster is marked exhausted");
            helper.assertValueEqual(manager.consume(level, chunk, 32), 0, "exhausted cluster gives nothing");
            helper.assertTrue(level.getBlockState(base).isAir(), "exhausted cluster block removed");

            manager.tick(level);
            helper.assertTrue(level.getBlockState(base).isAir(), "exhausted cluster stays removed");

            clock.addAndGet(resetMillis() + 60_000L);
            for (int tick = 0; tick < 25; tick++) {
                manager.tick(level);
            }
            ResourceClusterManager.ReserveView refilled = manager.reserveAt(chunk).orElseThrow();
            helper.assertValueEqual(refilled.remaining(), refilled.limit(), "cluster refilled");
            helper.assertFalse(refilled.timerRunning(), "timer reset until the next first extraction");
            helper.assertFalse(refilled.exhausted(), "cluster is no longer exhausted");
            helper.assertTrue(
                    level.getBlockState(base).is(cluster.type().block()),
                    "cluster blocks returned"
            );

            helper.assertValueEqual(manager.consume(level, chunk, 5), 5, "cluster can be mined again");
            helper.assertTrue(
                    manager.reserveAt(chunk).orElseThrow().timerRunning(),
                    "next first extraction starts a new timer"
            );
        } finally {
            ClusterClock.reset();
            manager.resetChunk(level, chunk.toLong());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cluster_commands", timeoutTicks = 600)
    public static void resetCommandClearsReserveAndTimer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        ChunkPos chunk = generateCluster(level, manager);
        helper.assertValueEqual(manager.consume(level, chunk, 7), 7, "reserve spent before the reset");
        helper.assertTrue(manager.reserveAt(chunk).orElseThrow().timerRunning(), "timer running before the reset");

        CommandSourceStack source = server.createCommandSourceStack()
                .withPosition(new Vec3(chunk.getMiddleBlockX(), level.getSeaLevel(), chunk.getMiddleBlockZ()))
                .withSuppressedOutput();
        helper.assertTrue(
                run(server, source, "kingdoms cluster status") > 0,
                "status found the cluster under the source"
        );
        helper.assertTrue(
                run(server, source, "kingdoms cluster reset here") > 0,
                "reset here started"
        );
        helper.assertValueEqual(
                run(server, source, "kingdoms cluster reset all"),
                0,
                "reset all only asks for confirmation first"
        );

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertFalse(ClusterMaintenance.isRunning(), "maintenance job finished");
                    ResourceClusterManager.ReserveView reserve = manager.reserveAt(chunk).orElseThrow();
                    helper.assertValueEqual(reserve.remaining(), reserve.limit(), "reset refilled the cluster");
                    helper.assertFalse(reserve.timerRunning(), "reset cleared the timer");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "cluster_commands", timeoutTicks = 600)
    public static void regenerationMovesPlannedClusters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceClusterManager scratch = new ResourceClusterManager();
        helper.assertValueEqual(scratch.effectiveGeneration(), 0, "scratch manager starts at the base generation");
        List<String> before = plannedClusters(level, scratch);
        helper.assertTrue(!before.isEmpty(), "sample region plans clusters");

        scratch.beginRegeneration();
        helper.assertValueEqual(scratch.effectiveGeneration(), 1, "regeneration bumped the generation");
        List<String> after = plannedClusters(level, scratch);

        helper.assertTrue(!before.equals(after), "new generation moves or retypes clusters");
        helper.succeed();
    }

    private static List<String> plannedClusters(ServerLevel level, ResourceClusterManager manager) {
        BlockPos spawn = level.getSharedSpawnPos();
        ChunkPos origin = new ChunkPos(new BlockPos(spawn.getX() + 2_400, 0, spawn.getZ() + 2_400));
        List<String> planned = new ArrayList<>();
        for (int dx = 0; dx < 24; dx++) {
            for (int dz = 0; dz < 24; dz++) {
                ResourceClusterManager.ChunkDiagnostic diagnostic =
                        manager.diagnoseChunk(level, new ChunkPos(origin.x + dx, origin.z + dz));
                if (diagnostic.surfaceCandidateChunk() != null) {
                    planned.add(diagnostic.surfaceCandidateChunk() + ":" + diagnostic.surfaceType());
                }
            }
        }
        return planned;
    }

    private static int run(MinecraftServer server, CommandSourceStack source, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new GameTestAssertException("Command rejected: " + command + " (" + exception.getMessage() + ")");
        }
    }

    private static long resetMillis() {
        return ModConfigSpec.CLUSTER_RESET_DAYS.getAsInt() * DAY_MILLIS;
    }

    private static ChunkPos generateCluster(ServerLevel level, ResourceClusterManager manager) {
        BlockPos spawn = level.getSharedSpawnPos();
        ChunkPos origin = new ChunkPos(new BlockPos(spawn.getX() + 1_200, 0, spawn.getZ() + 1_200));
        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                ChunkPos candidate = new ChunkPos(origin.x + dx, origin.z + dz);
                ResourceClusterManager.ChunkDiagnostic diagnostic = manager.diagnoseChunk(level, candidate);
                if (diagnostic.zone() != ResourceZone.YELLOW
                        || !candidate.equals(diagnostic.surfaceCandidateChunk())) {
                    continue;
                }
                level.getChunk(candidate.x, candidate.z);
                manager.queue(candidate, level.getGameTime() - 1L);
                manager.tick(level);
                if (manager.clusterAt(candidate).isPresent()) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("No yellow surface cluster found for the reserve test");
    }

    private ClusterReserveGameTests() {
    }
}
