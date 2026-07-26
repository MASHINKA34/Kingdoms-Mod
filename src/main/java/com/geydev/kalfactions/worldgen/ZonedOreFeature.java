package com.geydev.kalfactions.worldgen;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

public final class ZonedOreFeature extends Feature<NoneFeatureConfiguration> {
    private static List<OreVein> veins;

    public ZonedOreFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        double multiplier = sizeMultiplier(level, origin);
        if (multiplier <= 0.0D) {
            return false;
        }
        RandomSource random = context.random();
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 1;
        boolean placed = false;
        for (OreVein vein : veins()) {
            int size = (int) Math.round(vein.size() * multiplier);
            if (size <= 0) {
                continue;
            }
            int low = Math.max(minY, vein.minY());
            int high = Math.min(maxY, vein.maxY());
            if (high < low) {
                continue;
            }
            OreConfiguration configuration = new OreConfiguration(
                    List.of(
                            OreConfiguration.target(
                                    new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                                    vein.stone().defaultBlockState()
                            ),
                            OreConfiguration.target(
                                    new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),
                                    vein.deepslate().defaultBlockState()
                            )
                    ),
                    size,
                    vein.discardChanceOnAirExposure()
            );
            for (int attempt = 0; attempt < vein.count(); attempt++) {
                BlockPos center = new BlockPos(
                        origin.getX() + random.nextInt(16),
                        low + random.nextInt(high - low + 1),
                        origin.getZ() + random.nextInt(16)
                );
                placed |= Feature.ORE.place(new FeaturePlaceContext<>(
                        Optional.empty(),
                        level,
                        context.chunkGenerator(),
                        random,
                        center,
                        configuration
                ));
            }
        }
        return placed;
    }

    public static double sizeMultiplier(ResourceZone zone) {
        try {
            return switch (zone) {
                case BLUE -> ModConfigSpec.RESOURCE_BLUE_SIZE_MULTIPLIER.getAsDouble();
                case YELLOW -> ModConfigSpec.RESOURCE_YELLOW_SIZE_MULTIPLIER.getAsDouble();
                case RED -> ModConfigSpec.RESOURCE_RED_SIZE_MULTIPLIER.getAsDouble();
                case BLACK -> ModConfigSpec.RESOURCE_BLACK_SIZE_MULTIPLIER.getAsDouble();
            };
        } catch (IllegalStateException | IllegalArgumentException exception) {
            return zone.defaultOreSizeMultiplier();
        }
    }

    public static ResourceZone zoneAt(int blockX, int blockZ, int spawnX, int spawnZ) {
        double blue = 200.0D;
        double yellow = 5_000.0D;
        double red = 8_000.0D;
        try {
            blue = ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt();
            yellow = Math.max(blue, ModConfigSpec.RESOURCE_YELLOW_RADIUS.getAsInt());
            red = Math.max(yellow, ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt());
        } catch (IllegalStateException | IllegalArgumentException exception) {
            blue = 200.0D;
            yellow = 5_000.0D;
            red = 8_000.0D;
        }
        return ResourceZone.at(blockX, blockZ, spawnX, spawnZ, blue, yellow, red);
    }

    private static double sizeMultiplier(WorldGenLevel level, BlockPos origin) {
        BlockPos spawn = level.getLevel().getSharedSpawnPos();
        return sizeMultiplier(zoneAt(origin.getX(), origin.getZ(), spawn.getX(), spawn.getZ()));
    }

    private static synchronized List<OreVein> veins() {
        if (veins == null) {
            veins = List.of(
                    new OreVein(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 17, 20, 0, 190, 0.0F),
                    new OreVein(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 17, 20, 136, 256, 0.0F),
                    new OreVein(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 9, 10, -24, 56, 0.0F),
                    new OreVein(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 4, 10, -32, 232, 0.0F),
                    new OreVein(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 9, 6, 80, 256, 0.0F),
                    new OreVein(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, 10, 16, -16, 112, 0.0F),
                    new OreVein(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, 9, 4, -64, 32, 0.0F),
                    new OreVein(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, 9, 1, -64, -48, 0.0F),
                    new OreVein(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, 8, 4, -64, 15, 0.0F),
                    new OreVein(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, 8, 8, -64, -32, 0.0F),
                    new OreVein(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, 7, 2, -32, 32, 0.0F),
                    new OreVein(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, 7, 4, -64, 64, 0.5F),
                    new OreVein(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, 8, 7, -144, 16, 0.5F),
                    new OreVein(
                            createOre("zinc_ore", Blocks.IRON_ORE),
                            createOre("deepslate_zinc_ore", Blocks.DEEPSLATE_IRON_ORE),
                            12,
                            8,
                            -48,
                            96,
                            0.0F
                    )
            );
        }
        return veins;
    }

    private static Block createOre(String path, Block fallback) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
        return block == Blocks.AIR ? fallback : block;
    }

    private record OreVein(
            Block stone,
            Block deepslate,
            int size,
            int count,
            int minY,
            int maxY,
            float discardChanceOnAirExposure
    ) {
    }
}
