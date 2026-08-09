package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.integration.xaero.archive.XaeroRegionCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

public final class ScoutTerrainSampler {
    private static final int TILE_SIZE = 16;
    private static final boolean SHOW_FLOWERS = true;
    private static final int MAX_OPACITY = 15;

    private final ServerLevel level;
    private final Map<BlockState, CompoundTag> stateTags = new HashMap<>();
    private final Map<BlockState, Boolean> overlayStates = new HashMap<>();
    private final Map<BlockState, Boolean> invisibleStates = new HashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public ScoutTerrainSampler(ServerLevel level) {
        this.level = level;
    }

    public List<XaeroRegionCodec.SurfacePixel> sample(ChunkAccess chunk) {
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();
        List<XaeroRegionCodec.SurfacePixel> pixels = new ArrayList<>(XaeroRegionCodec.PIXELS_PER_TILE);
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int z = 0; z < TILE_SIZE; z++) {
                pixels.add(samplePixel(chunk, originX + x, originZ + z));
            }
        }
        return pixels;
    }

    private XaeroRegionCodec.SurfacePixel samplePixel(ChunkAccess chunk, int blockX, int blockZ) {
        int lowY = level.getMinBuildHeight();
        int ceiling = level.getMaxBuildHeight() - 1;
        int highY = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ), ceiling);
        BlockState opaqueState = null;
        int opaqueY = lowY;
        int topY = lowY;
        int light = 0;
        int overlayBiomeY = Integer.MIN_VALUE;
        List<MutableOverlay> overlays = new ArrayList<>();

        for (int y = highY; y >= lowY; y--) {
            cursor.set(blockX, y, blockZ);
            BlockState state = chunk.getBlockState(cursor);
            FluidState fluidState = state.getFluidState();
            BlockState fluidBlock = fluidState.isEmpty() ? null : fluidState.createLegacyBlock();
            cursor.set(blockX, Math.min(y + 1, ceiling), blockZ);
            int workingLight = level.getBrightness(LightLayer.BLOCK, cursor);
            cursor.set(blockX, y, blockZ);

            if (fluidBlock != null) {
                Outcome outcome = accept(fluidBlock, true, workingLight, blockX, y, blockZ, overlays);
                if (outcome != Outcome.SKIPPED) {
                    topY = Math.max(topY, y);
                }
                if (outcome == Outcome.OVERLAY && overlayBiomeY == Integer.MIN_VALUE) {
                    overlayBiomeY = y;
                }
                if (outcome == Outcome.OPAQUE) {
                    opaqueState = state;
                    opaqueY = y;
                    light = workingLight;
                    break;
                }
            }

            Block block = state.getBlock();
            if (block instanceof AirBlock || (fluidBlock != null && block == fluidBlock.getBlock())) {
                continue;
            }
            Outcome outcome = accept(state, false, workingLight, blockX, y, blockZ, overlays);
            if (outcome != Outcome.SKIPPED) {
                topY = Math.max(topY, y);
            }
            if (outcome == Outcome.OVERLAY && overlayBiomeY == Integer.MIN_VALUE) {
                overlayBiomeY = y;
            }
            if (outcome == Outcome.OPAQUE) {
                opaqueState = state;
                opaqueY = y;
                light = workingLight;
                break;
            }
        }

        BlockState written = opaqueState == null ? Blocks.AIR.defaultBlockState() : opaqueState;
        int biomeY = Mth.clamp(overlayBiomeY == Integer.MIN_VALUE ? topY : overlayBiomeY, lowY, ceiling);
        String biome = chunk
                .getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(biomeY), QuartPos.fromBlock(blockZ))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse(null);

        List<XaeroRegionCodec.SurfaceOverlay> encoded = new ArrayList<>(overlays.size());
        for (MutableOverlay overlay : overlays) {
            boolean water = overlay.state.getBlock() == Blocks.WATER;
            encoded.add(new XaeroRegionCodec.SurfaceOverlay(
                    water ? null : tagOf(overlay.state),
                    water,
                    overlay.light,
                    overlay.opacity
            ));
        }

        int height = clampHeight(opaqueY);
        return new XaeroRegionCodec.SurfacePixel(
                written.getBlock() == Blocks.GRASS_BLOCK ? null : tagOf(written),
                height,
                clampTopHeight(Math.max(topY, opaqueY), height),
                light,
                biome,
                encoded
        );
    }

    private Outcome accept(
            BlockState state,
            boolean fluid,
            int light,
            int blockX,
            int y,
            int blockZ,
            List<MutableOverlay> overlays
    ) {
        if (!fluid && isInvisible(state)) {
            return Outcome.SKIPPED;
        }
        if (shouldOverlay(state)) {
            addOverlay(state, light, blockX, y, blockZ, overlays);
            return Outcome.OVERLAY;
        }
        if (!hasVanillaColor(state, blockX, y, blockZ)) {
            return Outcome.SKIPPED;
        }
        return Outcome.OPAQUE;
    }

    private void addOverlay(
            BlockState state,
            int light,
            int blockX,
            int y,
            int blockZ,
            List<MutableOverlay> overlays
    ) {
        cursor.set(blockX, y, blockZ);
        int opacity = Math.min(MAX_OPACITY, state.getLightBlock(level, cursor));
        MutableOverlay current = overlays.isEmpty() ? null : overlays.getLast();
        if (current == null || current.state != state) {
            if (overlays.size() >= XaeroRegionCodec.MAX_OVERLAYS) {
                if (current != null) {
                    current.increaseOpacity(opacity);
                }
                return;
            }
            current = new MutableOverlay(state, light);
            overlays.add(current);
        }
        current.increaseOpacity(opacity);
    }

    private boolean shouldOverlay(BlockState state) {
        return overlayStates.computeIfAbsent(state, key -> {
            Block block = key.getBlock();
            return block instanceof AirBlock || block instanceof TransparentBlock || block == Blocks.WATER;
        });
    }

    private boolean isInvisible(BlockState state) {
        return invisibleStates.computeIfAbsent(state, key -> {
            Block block = key.getBlock();
            if (!(block instanceof LiquidBlock) && key.getRenderShape() == RenderShape.INVISIBLE) {
                return true;
            }
            if (block == Blocks.TORCH || block == Blocks.SHORT_GRASS) {
                return true;
            }
            if (block == Blocks.GLASS || block == Blocks.GLASS_PANE) {
                return true;
            }
            boolean flower = block instanceof PitcherCropBlock
                    || block instanceof TallFlowerBlock
                    || block instanceof FlowerBlock
                    || key.is(BlockTags.FLOWERS);
            if (block instanceof DoublePlantBlock && !flower) {
                return true;
            }
            return flower && !SHOW_FLOWERS;
        });
    }

    private boolean hasVanillaColor(BlockState state, int blockX, int y, int blockZ) {
        cursor.set(blockX, y, blockZ);
        MapColor mapColor;
        try {
            mapColor = state.getMapColor(level, cursor);
        } catch (RuntimeException exception) {
            return false;
        }
        return mapColor != null && mapColor.col != 0;
    }

    private CompoundTag tagOf(BlockState state) {
        return stateTags.computeIfAbsent(state, NbtUtils::writeBlockState);
    }

    private static int clampHeight(int height) {
        return Mth.clamp(height, XaeroRegionCodec.MIN_HEIGHT, XaeroRegionCodec.MAX_HEIGHT);
    }

    private static int clampTopHeight(int topHeight, int height) {
        int clamped = clampHeight(topHeight);
        return (clamped & 255) == (height & 255) ? height : clamped;
    }

    private enum Outcome {
        SKIPPED,
        OVERLAY,
        OPAQUE
    }

    private static final class MutableOverlay {
        private final BlockState state;
        private final int light;
        private int opacity;

        private MutableOverlay(BlockState state, int light) {
            this.state = state;
            this.light = light;
        }

        private void increaseOpacity(int amount) {
            opacity = Math.min(MAX_OPACITY, opacity + Math.min(MAX_OPACITY, amount));
        }
    }
}
