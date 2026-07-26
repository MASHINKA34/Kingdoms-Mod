package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager.ChunkDiagnostic;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.worldgen.ZonedOreFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResourceRuntimeGameTests {
    @GameTest(template = "empty", batch = "resource_runtime", timeoutTicks = 200)
    public static void oreSizeMultiplierFollowsZone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();

        helper.assertValueEqual(
                ZonedOreFeature.zoneAt(spawn.getX(), spawn.getZ(), spawn.getX(), spawn.getZ()),
                ResourceZone.BLUE,
                "spawn is blue"
        );
        helper.assertValueEqual(
                ZonedOreFeature.zoneAt(spawn.getX() + 2_000, spawn.getZ(), spawn.getX(), spawn.getZ()),
                ResourceZone.YELLOW,
                "2k is yellow"
        );
        helper.assertValueEqual(
                ZonedOreFeature.zoneAt(spawn.getX() + 6_000, spawn.getZ(), spawn.getX(), spawn.getZ()),
                ResourceZone.RED,
                "6k is red"
        );
        helper.assertValueEqual(
                ZonedOreFeature.zoneAt(spawn.getX() + 9_000, spawn.getZ(), spawn.getX(), spawn.getZ()),
                ResourceZone.BLACK,
                "9k is black"
        );

        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.BLUE), 0.0D, "blue has no ore");
        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.YELLOW), 0.5D, "yellow ore size");
        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.RED), 1.1D, "red ore size");
        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.BLACK), 1.7D, "black ore size");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_runtime", timeoutTicks = 200)
    public static void zonedOreFeatureIsRegisteredAndAttachedToOverworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "zoned_ores");

        helper.assertTrue(
                BuiltInRegistries.FEATURE.containsKey(id),
                "zoned_ores feature registered"
        );
        helper.assertTrue(
                level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(id),
                "zoned_ores configured feature loaded"
        );
        Registry<PlacedFeature> placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        helper.assertTrue(placed.containsKey(id), "zoned_ores placed feature loaded");

        Holder<PlacedFeature> holder = placed.getHolderOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, id));
        boolean attached = level.getBiome(helper.absolutePos(BlockPos.ZERO))
                .value()
                .getGenerationSettings()
                .features()
                .stream()
                .anyMatch(step -> step.stream().anyMatch(entry -> entry.value() == holder.value()));
        helper.assertTrue(attached, "zoned_ores added to overworld biome generation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_runtime", timeoutTicks = 400)
    public static void surfaceClustersSpawnOnlyInYellowAndRed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        BlockPos spawn = level.getSharedSpawnPos();

        ChunkPos blue = new ChunkPos(spawn);
        ChunkDiagnostic blueDiagnostic = manager.diagnoseChunk(level, blue);
        helper.assertValueEqual(blueDiagnostic.zone(), ResourceZone.BLUE, "blue zone");
        helper.assertTrue(blueDiagnostic.surfaceCandidateChunk() == null, "blue surface cluster disabled");

        ChunkPos black = new ChunkPos(new BlockPos(spawn.getX() + 9_000, 0, spawn.getZ()));
        ChunkDiagnostic blackDiagnostic = manager.diagnoseChunk(level, black);
        helper.assertValueEqual(blackDiagnostic.zone(), ResourceZone.BLACK, "black zone");
        helper.assertTrue(blackDiagnostic.surfaceCandidateChunk() == null, "black surface cluster disabled");

        generateSurfaceCluster(helper, manager, findSurfaceCandidate(level, manager, ResourceZone.YELLOW));
        generateSurfaceCluster(helper, manager, findSurfaceCandidate(level, manager, ResourceZone.RED));
        helper.succeed();
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
