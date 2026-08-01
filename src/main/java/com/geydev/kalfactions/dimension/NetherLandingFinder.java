package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.dimension.DimensionControlManager.LandingPos;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class NetherLandingFinder {
    private static final int MIN_Y = 32;
    private static final int MAX_Y = 112;

    public static Optional<LandingPos> find(
            ServerLevel level,
            List<LandingPos> occupied,
            LandingPos previous,
            NetherRules rules
    ) {
        for (BlockPos center : candidateCenters(level, occupied, previous, rules)) {
            ChunkPos chunk = new ChunkPos(center);
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                continue;
            }
            Optional<LandingPos> landing = findInLoadedChunk(level, center);
            if (landing.isPresent()) {
                return landing;
            }
        }
        return Optional.empty();
    }

    public static List<BlockPos> candidateCenters(
            ServerLevel level,
            List<LandingPos> occupied,
            LandingPos previous,
            NetherRules rules
    ) {
        WorldBorder border = level.getWorldBorder();
        List<BlockPos> candidates = new ArrayList<>();
        for (int attempt = 0; attempt < rules.landingAttempts(); attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            int radius = Mth.nextInt(level.getRandom(), rules.landingMinRadius(), rules.landingMaxRadius());
            int rawX = Mth.floor(Math.cos(angle) * radius);
            int rawZ = Mth.floor(Math.sin(angle) * radius);
            int x = (rawX >> 4) * 16 + 8;
            int z = (rawZ >> 4) * 16 + 8;
            if (!platformInsideBorder(border, x, z) || tooClose(x, z, occupied, previous, rules)) {
                continue;
            }
            BlockPos candidate = new BlockPos(x, 64, z);
            if (!candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    public static Optional<LandingPos> findInLoadedChunk(ServerLevel level, BlockPos center) {
        Candidate fallback = null;
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            BlockPos feet = new BlockPos(center.getX(), y, center.getZ());
            if (isSafe(level, feet)) {
                return Optional.of(new LandingPos(feet.getX(), feet.getY(), feet.getZ()));
            }
            if (fallback == null && hasOpenVolume(level, feet) && canBuildPlatform(level, feet)) {
                fallback = new Candidate(feet.getX(), feet.getY(), feet.getZ());
            }
        }
        if (fallback == null) {
            return Optional.empty();
        }
        buildPlatform(level, fallback);
        return Optional.of(new LandingPos(fallback.x, fallback.y, fallback.z));
    }

    public static Optional<LandingPos> findNear(ServerLevel level, BlockPos origin) {
        for (int radius = 0; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = -4; dy <= 4; dy++) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (isSafe(level, feet)) {
                            return Optional.of(new LandingPos(feet.getX(), feet.getY(), feet.getZ()));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (feet.getY() < MIN_Y || feet.getY() > MAX_Y || !hasOpenVolume(level, feet)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floorPos = feet.offset(dx, -1, dz);
                BlockState floor = level.getBlockState(floorPos);
                if (floor.isAir() || !floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, floorPos, net.minecraft.core.Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasOpenVolume(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() || !state.getFluidState().isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean tooClose(int x, int z, List<LandingPos> occupied, LandingPos previous, NetherRules rules) {
        long required = (long) rules.landingMinimumSeparation() * rules.landingMinimumSeparation();
        for (LandingPos other : occupied) {
            long dx = (long) x - other.x();
            long dz = (long) z - other.z();
            if (dx * dx + dz * dz < required) {
                return true;
            }
        }
        if (previous != null) {
            long dx = (long) x - previous.x();
            long dz = (long) z - previous.z();
            return dx == 0L && dz == 0L || dx * dx + dz * dz < required;
        }
        return false;
    }

    private static boolean platformInsideBorder(WorldBorder border, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!border.isWithinBounds(new BlockPos(x + dx, 64, z + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean canBuildPlatform(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState state = level.getBlockState(feet.offset(dx, -1, dz));
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void buildPlatform(ServerLevel level, Candidate candidate) {
        BlockPos center = new BlockPos(candidate.x, candidate.y, candidate.z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(center.offset(dx, -1, dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
    }

    private record Candidate(int x, int y, int z) {
    }

    private NetherLandingFinder() {
    }
}
