package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

final class QuarryStructurePlacer {
    private static final ResourceLocation LEVEL_ZERO =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "quarry/level_0");
    private static final int FOOTPRINT_SIZE = 48;
    private static final int GROUND_LEVEL = 3;
    private static final int MAX_LOWERING = 6;
    private static final int[] TERRAIN_SAMPLE_OFFSETS = {0, 8, 16, 24, 32, 40, 47};

    static Optional<Placement> planLevelZero(ServerLevel level, ChunkPos center) {
        StructureTemplate template = level.getStructureManager().get(LEVEL_ZERO).orElse(null);
        if (template == null) {
            KalFactions.LOGGER.error("Missing quarry structure {}", LEVEL_ZERO);
            return Optional.empty();
        }
        Vec3i size = template.getSize();
        if (size.getX() != FOOTPRINT_SIZE || size.getZ() != FOOTPRINT_SIZE || size.getY() < 1) {
            KalFactions.LOGGER.error("Invalid quarry structure size {}: {}", LEVEL_ZERO, size);
            return Optional.empty();
        }

        int minX = (center.x - QuarryManager.TERRITORY_RADIUS_CHUNKS) << 4;
        int minZ = (center.z - QuarryManager.TERRITORY_RADIUS_CHUNKS) << 4;
        int surfaceY = Math.max(
                level.getMinBuildHeight() + GROUND_LEVEL,
                terrainSurfaceY(level, center, minX, minZ)
        );
        BlockPos origin = new BlockPos(minX, surfaceY - GROUND_LEVEL, minZ);
        BlockPos maximum = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        if (origin.getY() < level.getMinBuildHeight()
                || maximum.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(origin)
                || !level.getWorldBorder().isWithinBounds(maximum)) {
            return Optional.empty();
        }

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        List<StructureTemplate.StructureBlockInfo> cores =
                template.filterBlocks(origin, settings, ModBlocks.QUARRY_CORE.get());
        if (cores.size() != 1) {
            KalFactions.LOGGER.error("Quarry structure {} must contain exactly one quarry core", LEVEL_ZERO);
            return Optional.empty();
        }
        return Optional.of(new Placement(template, settings, origin, cores.getFirst().pos().immutable()));
    }

    static int adjustedSurfaceY(int centerSurfaceY, int[] terrainSamples) {
        if (terrainSamples.length == 0) {
            return centerSurfaceY;
        }
        int[] sorted = terrainSamples.clone();
        Arrays.sort(sorted);
        int lowerQuartile = sorted[(sorted.length - 1) / 4];
        return Math.max(centerSurfaceY - MAX_LOWERING, Math.min(centerSurfaceY, lowerQuartile));
    }

    private static int terrainSurfaceY(ServerLevel level, ChunkPos center, int minX, int minZ) {
        int centerSurfaceY = surfaceY(level, center.getMiddleBlockX(), center.getMiddleBlockZ());
        int[] samples = new int[TERRAIN_SAMPLE_OFFSETS.length * TERRAIN_SAMPLE_OFFSETS.length];
        int index = 0;
        for (int offsetX : TERRAIN_SAMPLE_OFFSETS) {
            for (int offsetZ : TERRAIN_SAMPLE_OFFSETS) {
                samples[index++] = surfaceY(level, minX + offsetX, minZ + offsetZ);
            }
        }
        return adjustedSurfaceY(centerSurfaceY, samples);
    }

    private static int surfaceY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    static boolean place(ServerLevel level, Placement placement) {
        ChunkPos minimum = new ChunkPos(placement.origin());
        ChunkPos maximum = new ChunkPos(placement.origin().offset(
                placement.template().getSize().getX() - 1,
                0,
                placement.template().getSize().getZ() - 1
        ));
        for (int chunkX = minimum.x; chunkX <= maximum.x; chunkX++) {
            for (int chunkZ = minimum.z; chunkZ <= maximum.z; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
        return placement.template().placeInWorld(
                level,
                placement.origin(),
                placement.origin(),
                placement.settings(),
                level.getRandom(),
                Block.UPDATE_CLIENTS
        ) && level.getBlockState(placement.core()).is(ModBlocks.QUARRY_CORE.get());
    }

    record Placement(
            StructureTemplate template,
            StructurePlaceSettings settings,
            BlockPos origin,
            BlockPos core
    ) {
    }

    private QuarryStructurePlacer() {
    }
}
