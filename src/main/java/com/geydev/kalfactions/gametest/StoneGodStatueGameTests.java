package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.StoneGodStatueBlock;
import com.geydev.kalfactions.block.StoneGodStatueBlockEntity;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoneGodStatueGameTests {
    @GameTest(template = "empty")
    public static void stoneGodStatuesBuildAndRemoveEightSegments(GameTestHelper helper) {
        List<StoneGodStatueBlock> statues = List.of(
                ModBlocks.RESEARCH_GOD_STONE_8BLOCKS.get(),
                ModBlocks.WAR_GOD_STONE_8BLOCKS.get(),
                ModBlocks.ECONOMY_GOD_STONE_8BLOCKS.get()
        );
        for (int index = 0; index < statues.size(); index++) {
            StoneGodStatueBlock block = statues.get(index);
            BlockPos base = helper.absolutePos(new BlockPos(1 + index * 2, 1, 1));
            BlockState state = block.defaultBlockState();
            helper.getLevel().setBlock(base, state, Block.UPDATE_ALL);
            block.setPlacedBy(helper.getLevel(), base, state, null, ItemStack.EMPTY);

            for (int segment = 0; segment < StoneGodStatueBlock.HEIGHT; segment++) {
                BlockState segmentState = helper.getLevel().getBlockState(base.above(segment));
                helper.assertTrue(segmentState.is(block), "Missing statue segment " + segment);
                helper.assertTrue(
                        segmentState.getValue(StoneGodStatueBlock.SEGMENT) == segment,
                        "Wrong statue segment index at " + segment
                );
            }
            helper.assertTrue(
                    helper.getLevel().getBlockEntity(base) instanceof StoneGodStatueBlockEntity,
                    "Stone statue base has no block entity"
            );
            for (int segment = 1; segment < StoneGodStatueBlock.HEIGHT; segment++) {
                helper.assertTrue(
                        helper.getLevel().getBlockEntity(base.above(segment)) == null,
                        "Upper statue segment unexpectedly has a block entity"
                );
            }

            helper.getLevel().destroyBlock(base.above(4), false);
            for (int segment = 0; segment < StoneGodStatueBlock.HEIGHT; segment++) {
                helper.assertTrue(
                        helper.getLevel().getBlockState(base.above(segment)).is(Blocks.AIR),
                        "Statue segment survived removal at " + segment
                );
            }
        }
        helper.succeed();
    }
}
