package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlazeBurnerRecipeGameTests {
    @GameTest(template = "empty", batch = "blaze_burner_recipe")
    public static void magmaPatternAssemblesTheFilledBurner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeHolder<?> holder = level.getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "blaze_burner"))
                .orElse(null);
        helper.assertTrue(holder != null, "kingdoms:blaze_burner recipe did not load");
        helper.assertTrue(holder.value() instanceof CraftingRecipe, "kingdoms:blaze_burner is not a crafting recipe");

        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_BLOCK),
                new ItemStack(Items.MAGMA_BLOCK), new ItemStack(item("create:empty_blaze_burner")), new ItemStack(Items.MAGMA_BLOCK),
                new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.OMINOUS_BOTTLE), new ItemStack(Items.LAVA_BUCKET)
        ));
        helper.assertTrue(recipe.matches(input, level), "blaze burner pattern does not match its own recipe");

        ItemStack result = recipe.assemble(input, level.registryAccess());
        helper.assertTrue(result.is(item("create:blaze_burner")), "recipe result is not create:blaze_burner, was " + result);
        helper.assertValueEqual(result.getCount(), 1, "blaze burner result count");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "blaze_burner_recipe")
    public static void lavaBucketsComeBackEmpty(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeHolder<?> holder = level.getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "blaze_burner"))
                .orElse(null);
        helper.assertTrue(holder != null, "kingdoms:blaze_burner recipe did not load");

        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_BLOCK),
                new ItemStack(Items.MAGMA_BLOCK), new ItemStack(item("create:empty_blaze_burner")), new ItemStack(Items.MAGMA_BLOCK),
                new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.OMINOUS_BOTTLE), new ItemStack(Items.LAVA_BUCKET)
        ));
        java.util.List<ItemStack> remaining = recipe.getRemainingItems(input);
        helper.assertValueEqual(remaining.size(), 9, "remaining item count");
        for (int slot : new int[]{6, 8}) {
            helper.assertTrue(
                    remaining.get(slot).is(Items.BUCKET),
                    "lava bucket slot " + slot + " did not leave an empty bucket, was " + remaining.get(slot)
            );
        }
        helper.assertTrue(remaining.get(7).isEmpty(), "the ominous bottle should be consumed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "blaze_burner_recipe")
    public static void craftedBurnerPlacesWithABlazeAndHeatsWhenFed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos target = floor.above();
        level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atCenterOf(target.above(2)));
        ItemStack burner = new ItemStack(item("create:blaze_burner"));
        player.setItemInHand(InteractionHand.MAIN_HAND, burner);
        burner.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));

        BlockState placed = level.getBlockState(target);
        helper.assertTrue(placed.getBlock() instanceof BlazeBurnerBlock, "crafted burner did not place a blaze burner, was " + placed);
        HeatLevel idle = BlazeBurnerBlock.getHeatLevelOf(placed);
        helper.assertTrue(idle.isAtLeast(HeatLevel.SMOULDERING), "placed burner has no blaze, heat was " + idle);

        ItemStack coal = new ItemStack(Items.COAL, 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, coal);
        level.getBlockState(target).useItemOn(coal, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false));

        HeatLevel fed = BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(target));
        helper.assertTrue(fed.isAtLeast(HeatLevel.KINDLED), "burner did not take fuel, heat was " + fed);
        helper.assertTrue(HeatCondition.HEATED.testBlazeBurner(fed), "burner does not satisfy a heated basin recipe");
        helper.succeed();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private BlazeBurnerRecipeGameTests() {
    }
}
