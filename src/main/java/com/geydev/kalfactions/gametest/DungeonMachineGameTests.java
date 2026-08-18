package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.List;
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
public final class DungeonMachineGameTests {
    private static final int OUTSIDE_BLOCKS = 4;
    private static final int INSIDE_BLOCKS = 4;

    @GameTest(template = "empty", batch = "dungeon_machines", timeoutTicks = 400)
    public static void contraptionsAssembleBesideDungeonsWithoutTakingTheirBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DungeonManager manager = DungeonManager.get(level);
        BlockPos anchor = blackZoneAnchor(level);
        ChunkPos dungeonChunk = new ChunkPos(anchor);
        level.getChunk(dungeonChunk.x, dungeonChunk.z);
        level.getChunk(dungeonChunk.x - 1, dungeonChunk.z);

        DungeonManager.CreateResult created = manager.create(level, anchor, "Тест механизмов");
        helper.assertTrue(created.successful(), "the dungeon was created in the black zone");
        int dungeonId = created.dungeon().id();
        DungeonManager.MarkResult marked = manager.setClaims(
                level,
                dungeonId,
                List.of(ClaimKey.of(level, anchor)),
                true
        );
        helper.assertTrue(marked.changed() == 1, "the anchor chunk joined the dungeon");

        int y = anchor.getY();
        int z = anchor.getZ();
        int firstX = dungeonChunk.getMinBlockX() - OUTSIDE_BLOCKS;
        int lastX = dungeonChunk.getMinBlockX() + INSIDE_BLOCKS - 1;
        BlockPos start = new BlockPos(firstX, y, z);
        try {
            clear(level, firstX - 1, lastX + 1, y, z);
            for (int x = firstX; x <= lastX; x++) {
                level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }

            BlockPos insideDungeon = new BlockPos(dungeonChunk.getMinBlockX(), y, z);
            helper.assertFalse(
                    MachineProtection.canContraptionBreak(level, insideDungeon),
                    "a machine still may not change a dungeon block"
            );
            helper.assertTrue(
                    MachineProtection.canContraptionAct(level, insideDungeon),
                    "a machine may still operate inside a dungeon chunk"
            );

            TestContraption contraption = new TestContraption();
            boolean assembled;
            try {
                assembled = contraption.searchMovedStructure(level, start, Direction.EAST);
            } catch (AssemblyException exception) {
                helper.fail("assembly beside a dungeon threw " + exception.getMessage(), start);
                return;
            }
            helper.assertTrue(assembled, "a contraption beside a dungeon still assembles");

            int outside = 0;
            for (BlockPos local : contraption.getBlocks().keySet()) {
                BlockPos world = start.offset(local);
                helper.assertFalse(
                        new ChunkPos(world).equals(dungeonChunk),
                        "the contraption absorbed the dungeon block at " + world
                );
                if (world.getY() == y && world.getZ() == z) {
                    outside++;
                }
            }
            helper.assertValueEqual(outside, OUTSIDE_BLOCKS, "blocks taken from outside the dungeon");
        } finally {
            clear(level, firstX - 1, lastX + 1, y, z);
            manager.remove(dungeonId);
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

    private static BlockPos blackZoneAnchor(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockPos(
                spawn.getX() + 120_000,
                level.getSeaLevel() + 24,
                spawn.getZ() + 120_000
        );
    }

    private static final class TestContraption extends Contraption {
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

    private DungeonMachineGameTests() {
    }
}
