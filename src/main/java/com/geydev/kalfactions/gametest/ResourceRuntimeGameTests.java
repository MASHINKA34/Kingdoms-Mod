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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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
        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.YELLOW), 0.2D, "yellow ore size");
        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.RED), 0.5D, "red ore size");
        helper.assertValueEqual(ZonedOreFeature.sizeMultiplier(ResourceZone.BLACK), 1.1D, "black ore size");

        helper.assertValueEqual(ZonedOreFeature.minimumVeinSize(ResourceZone.YELLOW), 4, "yellow vein floor");
        helper.assertValueEqual(ZonedOreFeature.minimumVeinSize(ResourceZone.RED), 5, "red vein floor");
        helper.assertValueEqual(ZonedOreFeature.minimumVeinSize(ResourceZone.BLACK), 6, "black vein floor");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "resource_runtime", timeoutTicks = 600)
    public static void zonedOrePlacesRealBlocksWithPerZoneRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();

        OreReport blue = generateOre(level, spawn, 0, 96);
        OreReport yellow = generateOre(level, spawn, 2_000, 0);
        OreReport red = generateOre(level, spawn, 6_000, 0);
        OreReport black = generateOre(level, spawn, 9_000, 0);

        helper.assertTrue(blue.total() == 0, "blue zone has no ore, was " + blue.total());
        helper.assertTrue(yellow.total() > 0, "yellow zone places ore, was " + yellow.total());
        helper.assertTrue(red.total() > 0, "red zone places ore, was " + red.total());
        helper.assertTrue(black.total() > 0, "black zone places ore, was " + black.total());

        helper.assertTrue(yellow.diamond() == 0, "yellow zone has no diamond, was " + yellow.diamond());
        helper.assertTrue(yellow.lapis() == 0, "yellow zone has no lapis, was " + yellow.lapis());
        helper.assertTrue(yellow.coal() > 0, "yellow zone has coal, was " + yellow.coal());
        helper.assertTrue(yellow.copper() > 0, "yellow zone has copper, was " + yellow.copper());
        helper.assertTrue(
                yellow.iron() < yellow.copper(),
                "yellow iron is scarcer than copper: iron " + yellow.iron() + " vs copper " + yellow.copper()
        );
        helper.assertTrue(black.diamond() > 0, "black zone has diamond, was " + black.diamond());
        helper.assertTrue(black.lapis() > 0, "black zone has lapis, was " + black.lapis());
        helper.assertTrue(
                red.total() > yellow.total(),
                "red richer than yellow: " + red.total() + " vs " + yellow.total()
        );
        helper.assertTrue(
                black.total() > yellow.total(),
                "black richer than yellow: " + black.total() + " vs " + yellow.total()
        );
        helper.succeed();
    }

    private static OreReport generateOre(ServerLevel level, BlockPos spawn, int offsetX, int offsetZ) {
        ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + offsetX, 0, spawn.getZ() + offsetZ));
        int minY = -60;
        int maxY = 100;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    level.setBlock(cursor, y < 0
                            ? Blocks.DEEPSLATE.defaultBlockState()
                            : Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        new ZonedOreFeature().place(new FeaturePlaceContext<>(
                java.util.Optional.empty(),
                level,
                level.getChunkSource().getGenerator(),
                net.minecraft.util.RandomSource.create(0x4B494E47444F4DL + offsetX),
                new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()),
                NoneFeatureConfiguration.INSTANCE
        ));

        int coal = 0;
        int iron = 0;
        int copper = 0;
        int lapis = 0;
        int diamond = 0;
        int other = 0;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
                        coal++;
                    } else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
                        iron++;
                    } else if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) {
                        copper++;
                    } else if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
                        lapis++;
                    } else if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
                        diamond++;
                    } else if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                            || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
                        other++;
                    }
                }
            }
        }
        return new OreReport(coal, iron, copper, lapis, diamond, other);
    }

    private record OreReport(int coal, int iron, int copper, int lapis, int diamond, int other) {
        int total() {
            return coal + iron + copper + lapis + diamond + other;
        }
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
