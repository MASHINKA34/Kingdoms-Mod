package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.dimension.DimensionControlManager.PortalBounds;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class NetherPortalRegistration {
    public static final int MAX_AXIS_SIZE = 16;
    private static final int MAX_PORTAL_BLOCKS = MAX_AXIS_SIZE * MAX_AXIS_SIZE;
    private static final int MAX_VANILLA_PORTAL_BLOCKS = 21 * 21;

    public static Optional<PortalBounds> findConnectedPortal(ServerLevel level, BlockPos origin) {
        Optional<PortalBounds> connected = findConnectedPortalBlocks(level, origin);
        if (connected.isEmpty()) {
            return Optional.empty();
        }
        return connected;
    }

    static Optional<PortalBounds> findConnectedPortalBlocks(ServerLevel level, BlockPos origin) {
        BlockPos seed = findPortalSeed(level, origin).orElse(null);
        if (seed == null) {
            return Optional.empty();
        }
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.add(seed);
        int minX = seed.getX();
        int minY = seed.getY();
        int minZ = seed.getZ();
        int maxX = minX;
        int maxY = minY;
        int maxZ = minZ;
        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            if (!visited.add(current) || !level.getBlockState(current).is(Blocks.NETHER_PORTAL)) {
                continue;
            }
            if (visited.size() > MAX_PORTAL_BLOCKS) {
                return Optional.empty();
            }
            minX = Math.min(minX, current.getX());
            minY = Math.min(minY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxX = Math.max(maxX, current.getX());
            maxY = Math.max(maxY, current.getY());
            maxZ = Math.max(maxZ, current.getZ());
            if (maxX - minX + 1 > MAX_AXIS_SIZE
                    || maxY - minY + 1 > MAX_AXIS_SIZE
                    || maxZ - minZ + 1 > MAX_AXIS_SIZE) {
                return Optional.empty();
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && level.getBlockState(next).is(Blocks.NETHER_PORTAL)) {
                    pending.addLast(next);
                }
            }
        }
        return visited.isEmpty()
                ? Optional.empty()
                : Optional.of(new PortalBounds(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public static int clearConnectedPortal(ServerLevel level, BlockPos origin) {
        BlockPos seed = findPortalSeed(level, origin).orElse(null);
        if (seed == null) {
            return 0;
        }
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        pending.add(seed);
        int cleared = 0;
        while (!pending.isEmpty() && visited.size() <= MAX_VANILLA_PORTAL_BLOCKS) {
            BlockPos current = pending.removeFirst();
            if (!visited.add(current) || !level.getBlockState(current).is(Blocks.NETHER_PORTAL)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (!visited.contains(next) && level.getBlockState(next).is(Blocks.NETHER_PORTAL)) {
                    pending.addLast(next);
                }
            }
            level.setBlock(current, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            cleared++;
        }
        return cleared;
    }

    public static boolean isIntact(ServerLevel level, PortalBounds bounds) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.NETHER_PORTAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Optional<BlockPos> findPortalSeed(ServerLevel level, BlockPos origin) {
        if (level.getBlockState(origin).is(Blocks.NETHER_PORTAL)) {
            return Optional.of(origin.immutable());
        }
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = origin.relative(direction);
            if (level.getBlockState(adjacent).is(Blocks.NETHER_PORTAL)) {
                return Optional.of(adjacent.immutable());
            }
        }
        return Optional.empty();
    }

    private NetherPortalRegistration() {
    }
}
