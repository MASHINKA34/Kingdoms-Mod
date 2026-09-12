package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.CharonStatueBlock;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModCreativeTabs;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CharonStatueGameTests {
    @GameTest(template = "empty", batch = "charon_statue")
    public static void everyDirectionAndEveryBrokenPartDropsExactlyOneStatue(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.CHARON_STATUE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 1, 4));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int broken = 0; broken < 12; broken++) {
                var root = block.partState(facing, 0, 0, 0);
                level.setBlock(anchor, root, Block.UPDATE_ALL);
                block.setPlacedBy(level, anchor, root, null, ItemStack.EMPTY);
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 2; z++) {
                            var pos = CharonStatueBlock.partPos(anchor, facing, x, y, z);
                            var state = level.getBlockState(pos);
                            helper.assertTrue(state.equals(block.partState(facing, x, y, z)), "Missing or incorrect statue part");
                            helper.assertTrue(CharonStatueBlock.anchorPos(pos, state).equals(anchor), "Part resolved the wrong anchor");
                            var shape = state.getCollisionShape(level, pos, CollisionContext.empty());
                            helper.assertFalse(shape.isEmpty(), "Part has no collision");
                            var box = shape.bounds();
                            helper.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                                    && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1, "Collision escapes its cell");
                            helper.assertTrue(shape == state.getShape(level, pos), "Selection and collision differ");
                        }
                    }
                }
                BlockPos hit = CharonStatueBlock.partPos(anchor, facing, broken / 6, broken / 2 % 3, broken % 2);
                level.destroyBlock(hit, true);
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 2; z++) {
                            helper.assertFalse(level.getBlockState(CharonStatueBlock.partPos(anchor, facing, x, y, z)).is(block),
                                    "A part survived destruction");
                        }
                    }
                }
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(anchor).inflate(4));
                int count = drops.stream().filter(e -> e.getItem().is(ModItems.CHARON_STATUE.get()))
                        .mapToInt(e -> e.getItem().getCount()).sum();
                helper.assertValueEqual(count, 1, "statues dropped by breaking " + broken + " facing " + facing);
                drops.forEach(ItemEntity::discard);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue")
    public static void placementRequiresAllTwelveCellsAndFourSupports(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.CHARON_STATUE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(0);
        player.setPos(Vec3.atCenterOf(anchor.offset(4, 0, 4)));
        var stack = new ItemStack(ModItems.CHARON_STATUE.get(), 2);
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                level.setBlock(anchor.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(anchor.below()).add(0, .5, 0), Direction.UP, anchor.below(), false));
        helper.assertTrue(block.getStateForPlacement(context) != null, "A clear supported footprint was rejected");
        BlockPos obstruction = anchor.offset(1, 2, 1);
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(block.getStateForPlacement(context) == null, "An obstructed upper corner was accepted");
        helper.assertFalse(ModItems.CHARON_STATUE.get().place(context).consumesAction(), "Obstructed placement succeeded");
        helper.assertTrue(level.getBlockState(obstruction).is(Blocks.STONE), "Placement overwrote the obstruction");
        helper.assertValueEqual(stack.getCount(), 2, "items after refused placement");
        level.removeBlock(obstruction, false);
        level.removeBlock(anchor.offset(1, -1, 1), false);
        helper.assertTrue(block.getStateForPlacement(context) == null, "An unsupported corner was accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue")
    public static void snapshotRollbackDoesNotEraseRestoredNeighbors(GameTestHelper helper) {
        var level = helper.getLevel();
        var block = ModBlocks.CHARON_STATUE.get();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 1, 3));
        var root = block.defaultBlockState();
        level.setBlock(anchor, root, Block.UPDATE_ALL);
        block.setPlacedBy(level, anchor, root, null, ItemStack.EMPTY);
        level.restoringBlockSnapshots = true;
        try {
            // Undo the root first. Its onRemove must not recursively erase other snapshots.
            level.setBlock(anchor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(anchor.above()).is(block), "Rollback cascaded into other snapshots");
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 2; z++) {
                        if (x != 0 || y != 0 || z != 0) {
                            level.setBlock(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        } finally {
            level.restoringBlockSnapshots = false;
        }
        helper.assertTrue(level.getBlockState(anchor).is(Blocks.STONE), "Restored block was erased");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "charon_statue")
    public static void creativeTabAndPickBlockExposeTheStatueItem(GameTestHelper helper) {
        helper.assertTrue(ModCreativeTabs.creativeItems().contains(ModItems.CHARON_STATUE.get()), "Statue is absent from the creative tab");
        helper.assertTrue(ModBlocks.CHARON_STATUE.get().asItem() == ModItems.CHARON_STATUE.get(), "Pick block resolves the wrong item");
        helper.assertTrue(ModBlocks.CHARON_STATUE.get().defaultBlockState().getPistonPushReaction() == PushReaction.BLOCK,
                "A piston can split the structure");
        helper.succeed();
    }

    private CharonStatueGameTests() {
    }
}
