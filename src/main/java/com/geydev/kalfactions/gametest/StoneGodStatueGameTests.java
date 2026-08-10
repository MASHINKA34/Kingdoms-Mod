package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.StoneGodStatueBlock;
import com.geydev.kalfactions.block.StoneGodStatueBlockEntity;
import com.geydev.kalfactions.block.StoneGodStatueCollisionBlock;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
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
        for (StoneGodStatueBlock block : statues) {
            BlockPos base = helper.absolutePos(new BlockPos(4, 1, 4));
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

            BlockPos collisionSegment = null;
            int collisionSegmentCount = 0;
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                    for (int segment = 0; segment < StoneGodStatueBlock.HEIGHT; segment++) {
                        BlockPos candidate = base.offset(offsetX, segment, offsetZ);
                        BlockState candidateState = helper.getLevel().getBlockState(candidate);
                        if (candidateState.getBlock() instanceof StoneGodStatueCollisionBlock) {
                            collisionSegmentCount++;
                            if (collisionSegment == null) {
                                collisionSegment = candidate.immutable();
                            }
                        }
                    }
                }
            }
            helper.assertTrue(collisionSegmentCount > 0, "Statue has no distributed collision segments");
            helper.assertTrue(collisionSegment != null, "Could not find a collision segment");
            BlockState collisionState = helper.getLevel().getBlockState(collisionSegment);
            helper.assertTrue(
                    !collisionState.getCollisionShape(
                            helper.getLevel(),
                            collisionSegment,
                            CollisionContext.empty()
                    ).isEmpty(),
                    "Distributed statue collision is empty"
            );

            helper.getLevel().destroyBlock(collisionSegment, false);
            for (int offsetX = -3; offsetX <= 3; offsetX++) {
                for (int offsetZ = -3; offsetZ <= 3; offsetZ++) {
                    for (int segment = 0; segment < StoneGodStatueBlock.HEIGHT; segment++) {
                        BlockPos remainingPos = base.offset(offsetX, segment, offsetZ);
                        BlockState remainingState = helper.getLevel().getBlockState(remainingPos);
                        helper.assertTrue(
                                !(remainingState.getBlock() instanceof StoneGodStatueBlock)
                                        && !(remainingState.getBlock() instanceof StoneGodStatueCollisionBlock),
                                "Statue collision or anchor survived removal at "
                                        + offsetX + "," + segment + "," + offsetZ + ": " + remainingState
                        );
                    }
                }
            }
        }
        helper.succeed();
    }
}
