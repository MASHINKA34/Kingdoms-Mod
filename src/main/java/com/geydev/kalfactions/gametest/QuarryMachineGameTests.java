package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.protection.MachineProtection;
import com.geydev.kalfactions.quarry.QuarryDistribution;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryMachineGameTests {
    private static final int OUTSIDE_BLOCKS = 4;
    private static final int INSIDE_BLOCKS = 4;

    @GameTest(template = "empty", batch = "quarry_machines", timeoutTicks = 400)
    public static void contraptionsAssembleBesideQuarriesWithoutTakingTheirBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        QuarryManager manager = QuarryManager.get(level);
        ChunkPos core = findQuarryCandidate(level, manager);
        level.getChunk(core.x, core.z);
        QuarryManager.CreateResult created = manager.createAtChunk(level, core);
        helper.assertTrue(
                created == QuarryManager.CreateResult.CREATED || created == QuarryManager.CreateResult.OVERLAP,
                "the quarry was created, got " + created
        );
        QuarryManager.QuarryView quarry = manager.all().stream()
                .filter(view -> new ChunkPos(view.core()).equals(core))
                .findFirst()
                .orElseThrow();

        ChunkPos westEdge = new ChunkPos(core.x - 1, core.z);
        level.getChunk(westEdge.x - 1, westEdge.z);
        int y = level.getSeaLevel() + 24;
        int z = westEdge.getMiddleBlockZ();
        int firstX = westEdge.getMinBlockX() - OUTSIDE_BLOCKS;
        int lastX = westEdge.getMinBlockX() + INSIDE_BLOCKS - 1;
        BlockPos start = new BlockPos(firstX, y, z);
        try {
            clear(level, firstX - 1, lastX + 1, y, z);
            for (int x = firstX; x <= lastX; x++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }

            BlockPos insideQuarry = new BlockPos(westEdge.getMinBlockX(), y, z);
            helper.assertTrue(
                    MachineProtection.protectsBlocks(level, insideQuarry),
                    "the quarry keeps its blocks protected"
            );
            helper.assertFalse(
                    MachineProtection.canContraptionBreak(level, insideQuarry),
                    "a machine still may not change a quarry block"
            );
            helper.assertTrue(
                    MachineProtection.canContraptionAct(level, insideQuarry),
                    "a machine may now operate inside a quarry chunk"
            );

            QuarryTestContraption contraption = new QuarryTestContraption();
            boolean assembled;
            try {
                assembled = contraption.searchMovedStructure(level, start, Direction.EAST);
            } catch (AssemblyException exception) {
                helper.fail("assembly beside a quarry threw " + exception.getMessage(), start);
                return;
            }
            helper.assertTrue(assembled, "a contraption beside a quarry still assembles");

            int outside = 0;
            for (BlockPos local : contraption.getBlocks().keySet()) {
                BlockPos world = start.offset(local);
                helper.assertFalse(
                        MachineProtection.protectsBlocks(level, world),
                        "the contraption absorbed the quarry block at " + world
                );
                if (world.getY() == y && world.getZ() == z) {
                    outside++;
                }
            }
            helper.assertValueEqual(outside, OUTSIDE_BLOCKS, "blocks taken from outside the quarry");
        } finally {
            clear(level, firstX - 1, lastX + 1, y, z);
            manager.removeByCore(level, quarry.core());
            level.removeBlock(quarry.core(), false);
        }
        helper.succeed();
    }

    private static void clear(ServerLevel level, int fromX, int toX, int y, int z) {
        for (int x = fromX; x <= toX; x++) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    level.setBlock(new BlockPos(x, y + offsetY, z + offsetZ), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static ChunkPos findQuarryCandidate(ServerLevel level, QuarryManager manager) {
        BlockPos spawn = level.getSharedSpawnPos();
        int red = com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt();
        for (int distance = red + 64; distance <= red + 1_800; distance += 16) {
            for (int offset = -6_000; offset <= 6_000; offset += 16) {
                ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ() + offset));
                if (!level.getWorldBorder().isWithinBounds(new BlockPos(
                        chunk.getMiddleBlockX(),
                        level.getSeaLevel(),
                        chunk.getMiddleBlockZ()
                ))) {
                    continue;
                }
                if (!QuarryDistribution.isCandidate(
                        level.getSeed() ^ 0x5155415252594C31L,
                        chunk.x,
                        chunk.z,
                        QuarryManager.MINIMUM_SPACING_CHUNKS
                )) {
                    continue;
                }
                boolean blocked = manager.all().stream().anyMatch(view -> {
                    ChunkPos existing = new ChunkPos(view.core());
                    return !existing.equals(chunk)
                            && Math.max(Math.abs(existing.x - chunk.x), Math.abs(existing.z - chunk.z))
                            <= QuarryManager.MINIMUM_SPACING_CHUNKS;
                });
                if (!blocked) {
                    return chunk;
                }
            }
        }
        throw new IllegalStateException("No valid quarry position found");
    }

    private static final class QuarryTestContraption extends Contraption {
        @Override
        public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
            return searchMovedStructure(world, pos, null);
        }

        @Override
        public boolean canBeStabilized(Direction facing, BlockPos localPos) {
            return false;
        }

        @Override
        public ContraptionType getType() {
            return AllContraptionTypes.BEARING.value();
        }

        @Override
        protected boolean isAnchoringBlockAt(BlockPos pos) {
            return false;
        }
    }

    private QuarryMachineGameTests() {
    }
}
