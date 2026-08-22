package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.GuideBoardBlock;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuideBoardGameTests {
    @GameTest(template = "empty")
    public static void guideBoardBuildsCollidesAndBreaksInEveryDirection(GameTestHelper helper) {
        GuideBoardBlock board = (GuideBoardBlock) ModBlocks.GUIDE_BOARD.get();
        BlockPos center = helper.absolutePos(new BlockPos(4, 1, 4));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState anchor = board.defaultBlockState().setValue(GuideBoardBlock.FACING, facing);
            helper.getLevel().setBlock(center, anchor, Block.UPDATE_ALL);
            board.setPlacedBy(helper.getLevel(), center, anchor, null, ItemStack.EMPTY);

            for (GuideBoardBlock.GuideBoardPart part : GuideBoardBlock.GuideBoardPart.values()) {
                BlockPos lowerPos = partPos(center, facing, part);
                for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                    BlockPos pos = half == DoubleBlockHalf.UPPER ? lowerPos.above() : lowerPos;
                    BlockState state = helper.getLevel().getBlockState(pos);
                    helper.assertTrue(state.is(board), "Missing guide board part at " + pos);
                    helper.assertTrue(state.getValue(GuideBoardBlock.FACING) == facing, "Wrong guide board facing at " + pos);
                    helper.assertTrue(state.getValue(GuideBoardBlock.PART) == part, "Wrong guide board part at " + pos);
                    helper.assertTrue(state.getValue(GuideBoardBlock.HALF) == half, "Wrong guide board half at " + pos);
                    VoxelShape selection = state.getShape(helper.getLevel(), pos, CollisionContext.empty());
                    VoxelShape collision = state.getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
                    helper.assertTrue(!selection.isEmpty(), "Empty guide board selection at " + pos);
                    helper.assertTrue(selection.toAabbs().equals(collision.toAabbs()), "Guide board shapes differ at " + pos);
                }
            }

            BlockPos removed = partPos(center, facing, GuideBoardBlock.GuideBoardPart.RIGHT).above();
            helper.getLevel().destroyBlock(removed, false);
            for (GuideBoardBlock.GuideBoardPart part : GuideBoardBlock.GuideBoardPart.values()) {
                BlockPos lowerPos = partPos(center, facing, part);
                helper.assertTrue(!helper.getLevel().getBlockState(lowerPos).is(board), "Lower guide board part survived removal");
                helper.assertTrue(!helper.getLevel().getBlockState(lowerPos.above()).is(board), "Upper guide board part survived removal");
            }
        }
        helper.succeed();
    }

    private static BlockPos partPos(BlockPos center, Direction facing, GuideBoardBlock.GuideBoardPart part) {
        Direction left = facing.getCounterClockWise();
        return switch (part) {
            case CENTER -> center;
            case LEFT -> center.relative(left);
            case RIGHT -> center.relative(left.getOpposite());
        };
    }

    private GuideBoardGameTests() {
    }
}
