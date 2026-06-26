package com.geydev.kalfactions.worldmap;

import java.awt.image.BufferedImage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldMapRenderJob {
    private static final int BRIGHT_HIGH = 255;
    private static final int BRIGHT_NORMAL = 220;
    private static final int BRIGHT_LOW = 180;

    private final ServerLevel level;
    private final int centerX;
    private final int centerZ;
    private final int regionBlocks;
    private final int resolution;
    private final double minX;
    private final double minZ;
    private final double blocksPerPixel;
    private final BufferedImage image;
    private final int[] northHeight;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private int px;
    private int pz;

    public WorldMapRenderJob(ServerLevel level, int centerX, int centerZ, int regionBlocks, int resolution) {
        this.level = level;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.regionBlocks = regionBlocks;
        this.resolution = resolution;
        this.minX = centerX - regionBlocks / 2.0;
        this.minZ = centerZ - regionBlocks / 2.0;
        this.blocksPerPixel = (double) regionBlocks / resolution;
        this.image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_ARGB);
        this.northHeight = new int[resolution];
    }

    public boolean isDone() {
        return pz >= resolution;
    }

    public int progressPercent() {
        return (int) (100L * pz / resolution);
    }

    public double progress() {
        return (pz * (long) resolution + px) / (double) ((long) resolution * resolution);
    }

    public ServerLevel level() {
        return level;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int regionBlocks() {
        return regionBlocks;
    }

    public int resolution() {
        return resolution;
    }

    public BufferedImage image() {
        return image;
    }

    public void tick(int budget) {
        int processed = 0;
        while (pz < resolution && processed < budget) {
            int blockX = Mth.floor(minX + (px + 0.5) * blocksPerPixel);
            int blockZ = Mth.floor(minZ + (pz + 0.5) * blocksPerPixel);
            sample(blockX, blockZ);
            processed++;
            px++;
            if (px >= resolution) {
                px = 0;
                pz++;
            }
        }
    }

    private void sample(int blockX, int blockZ) {
        ChunkAccess chunk = level.getChunk(blockX >> 4, blockZ >> 4, ChunkStatus.FULL, true);
        int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15);
        int previous = northHeight[px];
        northHeight[px] = height;
        if (height <= level.getMinBuildHeight()) {
            image.setRGB(px, pz, 0);
            return;
        }
        cursor.set(blockX, height - 1, blockZ);
        BlockState state = chunk.getBlockState(cursor);
        MapColor mapColor = state.getMapColor(level, cursor);
        if (mapColor == MapColor.NONE) {
            image.setRGB(px, pz, 0);
            return;
        }
        int brightness;
        if (pz == 0) {
            brightness = BRIGHT_NORMAL;
        } else {
            int delta = height - previous;
            brightness = delta > 0 ? BRIGHT_HIGH : (delta < 0 ? BRIGHT_LOW : BRIGHT_NORMAL);
        }
        int base = snowCovered(mapColor) ? MapColor.SNOW.col : mapColor.col;
        int r = ((base >> 16) & 255) * brightness / 255;
        int g = ((base >> 8) & 255) * brightness / 255;
        int b = (base & 255) * brightness / 255;
        image.setRGB(px, pz, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private boolean snowCovered(MapColor mapColor) {
        if (mapColor == MapColor.WATER || mapColor == MapColor.ICE || mapColor == MapColor.SNOW) {
            return false;
        }
        return level.getBiome(cursor).value().getPrecipitationAt(cursor) == Biome.Precipitation.SNOW;
    }
}
