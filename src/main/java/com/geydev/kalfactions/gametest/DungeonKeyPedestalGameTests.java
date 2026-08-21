package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonKeyPedestalActivation;
import com.geydev.kalfactions.block.DungeonKeyPedestalBlock;
import com.geydev.kalfactions.block.DungeonKeyPedestalBlockEntity;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonKeyPedestalGameTests {
    @GameTest(template = "empty", batch = "dungeon_key_pedestal")
    public static void allKeysActivateTheirMatchingState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<Item> keys = List.of(
                ModItems.GHOST_KEY.get(),
                ModItems.SCULK_KEY.get(),
                ModItems.INFERNAL_KEY.get(),
                ModItems.MOSSY_KEY.get()
        );
        List<DungeonKeyPedestalActivation> activations = List.of(
                DungeonKeyPedestalActivation.GHOST,
                DungeonKeyPedestalActivation.SCULK,
                DungeonKeyPedestalActivation.INFERNAL,
                DungeonKeyPedestalActivation.MOSSY
        );

        for (int index = 0; index < keys.size(); index++) {
            BlockPos pos = helper.absolutePos(new BlockPos(index, 1, 0));
            level.setBlock(pos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
            configure(level, pos, activations.get(index), DungeonKeyPedestalBlockEntity.DEFAULT_SIGNAL_TICKS);
            ItemStack stack = new ItemStack(keys.get(index), 2);
            use(level, pos, player, stack);
            BlockState state = level.getBlockState(pos);
            helper.assertValueEqual(state.getValue(DungeonKeyPedestalBlock.ACTIVATION), activations.get(index), "activation");
            helper.assertValueEqual(stack.getCount(), 1, "survival key count");
            helper.assertValueEqual(state.getSignal(level, pos, Direction.NORTH), 15, "weak signal");
            helper.assertValueEqual(state.getDirectSignal(level, pos, Direction.UP), 15, "direct signal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal")
    public static void wrongItemsAndActivePedestalsRejectKeys(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlock(pos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        configure(level, pos, DungeonKeyPedestalActivation.GHOST,
                DungeonKeyPedestalBlockEntity.DEFAULT_SIGNAL_TICKS);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack wrongItem = new ItemStack(Items.DIAMOND, 3);
        use(level, pos, player, wrongItem);
        helper.assertValueEqual(wrongItem.getCount(), 3, "wrong item count");
        helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.NONE,
                "wrong item activation"
        );

        ItemStack ghostKey = new ItemStack(ModItems.GHOST_KEY.get());
        use(level, pos, player, ghostKey);
        ItemStack mossyKey = new ItemStack(ModItems.MOSSY_KEY.get(), 2);
        use(level, pos, player, mossyKey);
        helper.assertValueEqual(mossyKey.getCount(), 2, "key count while active");
        helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.GHOST,
                "activation while active"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal")
    public static void creativeActivationDoesNotConsumeAKey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlock(pos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        configure(level, pos, DungeonKeyPedestalActivation.SCULK,
                DungeonKeyPedestalBlockEntity.DEFAULT_SIGNAL_TICKS);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack stack = new ItemStack(ModItems.SCULK_KEY.get(), 2);
        use(level, pos, player, stack);
        helper.assertValueEqual(stack.getCount(), 2, "creative key count");
        helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.SCULK,
                "creative activation"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal", timeoutTicks = 420)
    public static void activationLastsExactly400Ticks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlock(pos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        configure(level, pos, DungeonKeyPedestalActivation.INFERNAL, 400);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(level, pos, player, new ItemStack(ModItems.INFERNAL_KEY.get()));

        helper.runAfterDelay(399, () -> {
            BlockState state = level.getBlockState(pos);
            helper.assertValueEqual(
                    state.getValue(DungeonKeyPedestalBlock.ACTIVATION),
                    DungeonKeyPedestalActivation.INFERNAL,
                    "activation at tick 399"
            );
            helper.assertValueEqual(state.getSignal(level, pos, Direction.NORTH), 15, "signal at tick 399");
        });
        helper.runAfterDelay(400, () -> {
            BlockState state = level.getBlockState(pos);
            helper.assertValueEqual(
                    state.getValue(DungeonKeyPedestalBlock.ACTIVATION),
                    DungeonKeyPedestalActivation.NONE,
                    "activation at tick 400"
            );
            helper.assertValueEqual(state.getSignal(level, pos, Direction.NORTH), 0, "signal at tick 400");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal", timeoutTicks = 80)
    public static void activationUsesConfiguredSignalDuration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlock(pos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        configure(level, pos, DungeonKeyPedestalActivation.MOSSY, 60);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(level, pos, player, new ItemStack(ModItems.MOSSY_KEY.get()));

        helper.runAfterDelay(59, () -> helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.MOSSY,
                "activation at configured tick 59"
        ));
        helper.runAfterDelay(60, () -> {
            BlockState state = level.getBlockState(pos);
            helper.assertValueEqual(
                    state.getValue(DungeonKeyPedestalBlock.ACTIVATION),
                    DungeonKeyPedestalActivation.NONE,
                    "activation at configured tick 60"
            );
            helper.assertValueEqual(state.getSignal(level, pos, Direction.NORTH), 0, "configured signal ended");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal")
    public static void redstoneWireUpdatesAndBreakingStopsPower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pedestalPos = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos wirePos = helper.absolutePos(new BlockPos(1, 2, 0));
        level.setBlock(pedestalPos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(wirePos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pedestalPos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        configure(level, pedestalPos, DungeonKeyPedestalActivation.MOSSY,
                DungeonKeyPedestalBlockEntity.DEFAULT_SIGNAL_TICKS);
        level.setBlock(wirePos, Blocks.REDSTONE_WIRE.defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(level, pedestalPos, player, new ItemStack(ModItems.MOSSY_KEY.get()));

        helper.runAfterDelay(2, () -> {
            helper.assertValueEqual(level.getBlockState(wirePos).getValue(RedStoneWireBlock.POWER), 15, "powered wire");
            level.destroyBlock(pedestalPos, false);
        });
        helper.runAfterDelay(4, () -> {
            helper.assertValueEqual(level.getBlockState(wirePos).getValue(RedStoneWireBlock.POWER), 0, "wire after breaking");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal")
    public static void facingRotatesAndLootDropsThePedestal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockState state = ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState();
        helper.assertValueEqual(state.getValue(DungeonKeyPedestalBlock.FACING), Direction.NORTH, "default facing");
        helper.assertValueEqual(
                state.rotate(Rotation.CLOCKWISE_90).getValue(DungeonKeyPedestalBlock.FACING),
                Direction.EAST,
                "rotated facing"
        );
        level.setBlock(pos, state, Block.UPDATE_ALL);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        helper.assertValueEqual(drops.size(), 1, "loot stack count");
        helper.assertTrue(drops.getFirst().is(ModItems.DUNGEON_KEY_PEDESTAL.get()), "loot item was " + drops);
        helper.assertValueEqual(drops.getFirst().getCount(), 1, "loot item count");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dungeon_key_pedestal")
    public static void unconfiguredAndMismatchedKeysAreRejected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlock(pos, ModBlocks.DUNGEON_KEY_PEDESTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack ghostKey = new ItemStack(ModItems.GHOST_KEY.get(), 2);
        use(level, pos, player, ghostKey);
        helper.assertValueEqual(ghostKey.getCount(), 2, "unconfigured key count");
        helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.NONE,
                "unconfigured activation"
        );

        configure(level, pos, DungeonKeyPedestalActivation.SCULK, 40);
        use(level, pos, player, ghostKey);
        helper.assertValueEqual(ghostKey.getCount(), 2, "mismatched key count");
        helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.NONE,
                "mismatched activation"
        );

        ItemStack sculkKey = new ItemStack(ModItems.SCULK_KEY.get());
        use(level, pos, player, sculkKey);
        helper.assertValueEqual(sculkKey.getCount(), 0, "matching key count");
        helper.assertValueEqual(
                level.getBlockState(pos).getValue(DungeonKeyPedestalBlock.ACTIVATION),
                DungeonKeyPedestalActivation.SCULK,
                "matching activation"
        );
        helper.succeed();
    }

    private static void use(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        level.getBlockState(pos).useItemOn(
                stack,
                level,
                player,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
    }

    private static void configure(
            ServerLevel level,
            BlockPos pos,
            DungeonKeyPedestalActivation requiredKey,
            int signalTicks
    ) {
        if (!(level.getBlockEntity(pos) instanceof DungeonKeyPedestalBlockEntity pedestal)) {
            throw new IllegalStateException("Missing dungeon key pedestal block entity at " + pos);
        }
        pedestal.configure(requiredKey, signalTicks);
    }

    private DungeonKeyPedestalGameTests() {
    }
}
