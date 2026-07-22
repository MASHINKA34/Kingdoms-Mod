package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.dimension.DimensionControlManager.LandingPos;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        WorldBorder border = level.getWorldBorder();
        Candidate fallback = null;
        for (int attempt = 0; attempt < rules.landingAttempts(); attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            int radius = Mth.nextInt(level.getRandom(), rules.landingMinRadius(), rules.landingMaxRadius());
            int x = Mth.floor(Math.cos(angle) * radius);
            int z = Mth.floor(Math.sin(angle) * radius);
            BlockPos center = new BlockPos(x, 64, z);
            if (!platformInsideBorder(border, x, z) || tooClose(x, z, occupied, previous, rules)) {
                continue;
            }
            level.getChunk(x >> 4, z >> 4);
            for (int y = MAX_Y; y >= MIN_Y; y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (isSafe(level, feet)) {
                    return Optional.of(new LandingPos(x, y, z));
                }
                if (fallback == null && hasOpenVolume(level, feet) && canBuildPlatform(level, feet)) {
                    fallback = new Candidate(x, y, z);
                }
            }
        }
        if (fallback == null) {
            return Optional.empty();
        }
        buildPlatform(level, fallback);
        return Optional.of(new LandingPos(fallback.x, fallback.y, fallback.z));
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
