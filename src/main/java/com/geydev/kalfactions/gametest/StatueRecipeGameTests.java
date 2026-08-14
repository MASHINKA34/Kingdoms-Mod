package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.recipe.StatueCraftingHandler;
import com.geydev.kalfactions.recipe.StatueCraftingRecipe;
import com.geydev.kalfactions.registry.ModItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StatueRecipeGameTests {
    @GameTest(template = "empty", batch = "statue_recipes")
    public static void everyStatueRecipeLoads(GameTestHelper helper) {
        for (String id : List.of("statue_science", "war_god_statue", "economy_god_statue")) {
            StatueCraftingRecipe recipe = statueRecipe(helper, id);
            helper.assertValueEqual(recipe.sizedIngredients().size(), StatueCraftingRecipe.SIZE, id + " ingredient count");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "statue_recipes")
    public static void scienceStatueNeedsTheFullCounts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StatueCraftingRecipe recipe = statueRecipe(helper, "statue_science");

        helper.assertTrue(recipe.matches(scienceInput(16), level), "science pattern does not match its own recipe");
        helper.assertFalse(recipe.matches(scienceInput(15), level), "science pattern matched with too few crystals");

        ItemStack result = recipe.assemble(scienceInput(16), level.registryAccess());
        helper.assertTrue(result.is(ModItems.STATUE_SCIENCE.get()), "science recipe result is " + result);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "statue_recipes")
    public static void warStatueNeedsTheFullIronStacks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StatueCraftingRecipe recipe = statueRecipe(helper, "war_god_statue");

        helper.assertTrue(recipe.matches(warInput(64), level), "war pattern does not match its own recipe");
        helper.assertFalse(recipe.matches(warInput(32), level), "war pattern matched with half an iron stack");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "statue_recipes")
    public static void craftingConsumesTheWholeCounts(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory());
        TransientCraftingContainer container = new TransientCraftingContainer(menu, 3, 3);
        List<ItemStack> grid = scienceGrid(16);
        for (int slot = 0; slot < grid.size(); slot++) {
            container.setItem(slot, grid.get(slot));
        }

        StatueCraftingHandler.onItemCrafted(new PlayerEvent.ItemCraftedEvent(
                player, new ItemStack(ModItems.STATUE_SCIENCE.get()), container));

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            helper.assertValueEqual(container.getItem(slot).getCount(), 1, "slot " + slot + " left over after crafting");
        }
        helper.succeed();
    }

    private static StatueCraftingRecipe statueRecipe(GameTestHelper helper, String id) {
        RecipeHolder<?> holder = helper.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, id))
                .orElse(null);
        helper.assertTrue(holder != null, "kingdoms:" + id + " recipe did not load");
        helper.assertTrue(holder.value() instanceof StatueCraftingRecipe, "kingdoms:" + id + " is not a statue recipe");
        return (StatueCraftingRecipe) holder.value();
    }

    private static CraftingInput scienceInput(int crystals) {
        return CraftingInput.of(3, 3, scienceGrid(crystals));
    }

    private static List<ItemStack> scienceGrid(int crystals) {
        return List.of(
                new ItemStack(Items.DIAMOND, 4),
                new ItemStack(Items.ENCHANTING_TABLE),
                new ItemStack(Items.DIAMOND, 4),
                new ItemStack(Items.BOOKSHELF),
                new ItemStack(ModItems.CRYSTAL_SCIENCE.get(), crystals),
                new ItemStack(Items.BOOKSHELF),
                new ItemStack(Items.DEEPSLATE),
                new ItemStack(Items.LAPIS_BLOCK, 16),
                new ItemStack(Items.DEEPSLATE)
        );
    }

    private static CraftingInput warInput(int iron) {
        return CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.IRON_INGOT, iron),
                new ItemStack(Items.SMITHING_TABLE),
                new ItemStack(Items.IRON_INGOT, iron),
                new ItemStack(Items.DIAMOND_SWORD),
                new ItemStack(ModItems.CRYSTAL_MILITARY.get(), 16),
                new ItemStack(Items.DIAMOND_AXE),
                new ItemStack(Items.OBSIDIAN),
                new ItemStack(Items.MAGMA_BLOCK, 8),
                new ItemStack(Items.OBSIDIAN)
        ));
    }

    private StatueRecipeGameTests() {
    }
}
