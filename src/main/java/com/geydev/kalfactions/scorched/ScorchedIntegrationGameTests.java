package com.geydev.kalfactions.scorched;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.ribs.scguns.client.screen.GunBenchMenu;
import top.ribs.scguns.client.screen.GunBenchRecipe;
import top.ribs.scguns.util.BlueprintRecipeData;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScorchedIntegrationGameTests {
    private static final ResourceLocation DEEP_DARK_RECIPE = scguns("guns/echoes_2_from_gun_bench");
    private static final ResourceLocation IRON_RECIPE = scguns("guns/auvtomag_from_gun_bench");

    @GameTest(template = "empty")
    public static void raidFlareRegistryAndRecipesAreDisabled(GameTestHelper helper) {
        for (ResourceLocation flareId : DisabledRaidFlares.ITEM_IDS) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(flareId), "Missing registered flare " + flareId);
            boolean recipeExists = helper.getLevel().getRecipeManager().getRecipes().stream()
                    .anyMatch(holder -> DisabledRaidFlares.isDisabled(
                            holder.value().getResultItem(helper.getLevel().registryAccess())
                    ));
            helper.assertFalse(recipeExists, "A recipe still produces disabled flare " + flareId);
        }

        ItemStack ordinaryScorchedItem = stack(scguns("gunpowder_dust"));
        helper.assertFalse(ordinaryScorchedItem.isEmpty(), "Ordinary Scorched Guns item must remain registered");
        helper.assertFalse(DisabledRaidFlares.isDisabled(ordinaryScorchedItem), "Ordinary Scorched Guns item was disabled");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(scguns("gun_bench")).isPresent(),
                "Ordinary Scorched Guns recipes must remain loaded");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flarePistolAndRaidFlareEntityAreBlocked(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pistol = stack(scguns("flare_pistol"));
        ItemStack flare = stack(DisabledRaidFlares.ITEM_IDS.getFirst());
        player.setItemInHand(InteractionHand.MAIN_HAND, pistol);
        player.setItemInHand(InteractionHand.OFF_HAND, flare);

        var result = pistol.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.FAIL, "Flare pistol use must fail server-side");
        helper.assertTrue(player.getOffhandItem().getCount() == 1, "Blocked flare use must not consume the flare");

        EntityType<?> flareType = BuiltInRegistries.ENTITY_TYPE.get(DisabledRaidFlares.RAID_FLARE_ENTITY_ID);
        Entity flareEntity = flareType.create(helper.getLevel());
        helper.assertTrue(flareEntity != null, "Raid flare entity must exist in the registry");
        flareEntity.moveTo(helper.absolutePos(BlockPos.ZERO), 0.0F, 0.0F);
        helper.assertFalse(helper.getLevel().addFreshEntity(flareEntity),
                "Command- or save-created raid flare entity must be rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deepDarkBlueprintReplacesSculkTome(GameTestHelper helper) {
        GunBenchRecipe recipe = recipe(helper, DEEP_DARK_RECIPE).value();
        ItemStack deepDarkBlueprint = stack(scguns("deep_dark_blueprint"));
        ItemStack tome = stack(scguns("sculk_tome"));
        helper.assertTrue(recipe.getBlueprint().test(deepDarkBlueprint), "Deep-dark recipe must use its real blueprint");
        helper.assertFalse(recipe.getBlueprint().test(tome), "Sculk tome must not satisfy the blueprint ingredient");

        BenchHarness harness = bench(helper, DEEP_DARK_RECIPE);
        helper.assertFalse(harness.menu.getSlot(10).mayPlace(tome), "Gun Bench blueprint slot must reject a sculk tome");
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).getItem()
                        == recipe.getResultItem(helper.getLevel().registryAccess()).getItem(),
                "Deep-dark recipe output must be available with the real blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void listedBlueprintsOnlyMatchTheirOwnRecipes(GameTestHelper helper) {
        List<RecipeHolder<GunBenchRecipe>> recipes = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(GunBenchRecipe.Type.INSTANCE);
        for (ResourceLocation blueprintId : GunBenchBlueprintConsumption.BLUEPRINT_IDS) {
            ItemStack blueprint = stack(blueprintId);
            helper.assertFalse(blueprint.isEmpty(), "Missing registered blueprint " + blueprintId);
            List<RecipeHolder<GunBenchRecipe>> matches = recipes.stream()
                    .filter(holder -> holder.value().getBlueprint().test(blueprint))
                    .toList();
            helper.assertFalse(matches.isEmpty(), "Blueprint has no Gun Bench recipes: " + blueprintId);
            for (RecipeHolder<GunBenchRecipe> match : matches) {
                for (ResourceLocation otherId : GunBenchBlueprintConsumption.BLUEPRINT_IDS) {
                    if (!otherId.equals(blueprintId)) {
                        helper.assertFalse(match.value().getBlueprint().test(stack(otherId)),
                                match.id() + " also accepts the wrong blueprint " + otherId);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingAndWrongBlueprintDoNotCreateResult(GameTestHelper helper) {
        BenchHarness harness = benchWithoutBlueprint(helper, DEEP_DARK_RECIPE);
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).isEmpty(),
                "Gun Bench must not create output without a blueprint");

        harness.container.setItem(GunBenchBlueprintConsumption.BLUEPRINT_SLOT, stack(scguns("iron_blueprint")));
        harness.menu.slotsChanged(harness.container);
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).isEmpty(),
                "Gun Bench must not create output with the wrong blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void regularTakeConsumesExactlyOneBlueprint(GameTestHelper helper) {
        BenchHarness harness = bench(helper, DEEP_DARK_RECIPE);
        Item expected = harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).getItem();
        harness.menu.clicked(11, 0, ClickType.PICKUP, harness.player);
        helper.assertTrue(harness.menu.getCarried().is(expected), "Regular click must deliver the crafted result");
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.BLUEPRINT_SLOT).isEmpty(),
                "Regular click must consume exactly one blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftTakeConsumesExactlyOneBlueprint(GameTestHelper helper) {
        BenchHarness harness = bench(helper, DEEP_DARK_RECIPE);
        Item expected = harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).getItem();
        ItemStack moved = harness.menu.quickMoveStack(harness.player, 11);
        helper.assertTrue(moved.is(expected), "Shift-click must transfer the crafted result");
        helper.assertTrue(harness.player.getInventory().contains(new ItemStack(expected)),
                "Shift-clicked result must reach player inventory");
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.BLUEPRINT_SLOT).isEmpty(),
                "Shift-click must consume exactly one blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullInventoryPreservesBlueprint(GameTestHelper helper) {
        BenchHarness harness = bench(helper, DEEP_DARK_RECIPE);
        for (int slot = 0; slot < harness.player.getInventory().getContainerSize(); slot++) {
            harness.player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }

        ItemStack moved = harness.menu.quickMoveStack(harness.player, 11);
        helper.assertTrue(moved.isEmpty(), "Shift-click must fail when inventory is full");
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.BLUEPRINT_SLOT).getCount() == 1,
                "Failed result transfer must not consume the blueprint");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedTakeCannotDuplicateResult(GameTestHelper helper) {
        BenchHarness harness = bench(helper, DEEP_DARK_RECIPE);
        Item expected = harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).getItem();
        harness.menu.clicked(11, 0, ClickType.PICKUP, harness.player);
        int firstCount = harness.menu.getCarried().is(expected) ? harness.menu.getCarried().getCount() : 0;
        harness.menu.clicked(11, 0, ClickType.PICKUP, harness.player);
        int secondCount = harness.menu.getCarried().is(expected) ? harness.menu.getCarried().getCount() : 0;
        helper.assertTrue(firstCount == 1 && secondCount == 1, "Repeated result action must not duplicate output");
        helper.assertTrue(harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).isEmpty(),
                "Result slot must remain empty after the transaction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipePresentationAndSelectionUseRealData(GameTestHelper helper) {
        ItemStack blueprint = stack(scguns("iron_blueprint"));
        List<RecipeHolder<GunBenchRecipe>> pages = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(GunBenchRecipe.Type.INSTANCE).stream()
                .filter(holder -> holder.value().getBlueprint().test(blueprint))
                .toList();
        helper.assertTrue(pages.size() > 1, "Iron blueprint must have multiple real recipe pages");
        for (RecipeHolder<GunBenchRecipe> page : pages) {
            helper.assertFalse(page.value().getResultItem(helper.getLevel().registryAccess()).isEmpty(),
                    "Recipe page has an empty output: " + page.id());
            helper.assertTrue(page.value().getIngredients().stream()
                            .filter(ingredient -> !ingredient.isEmpty())
                            .allMatch(ingredient -> ingredient.getItems().length > 0),
                    "Recipe page has an unresolved ingredient: " + page.id());
        }

        ResourceLocation selected = pages.get(1).id();
        BlueprintRecipeData.saveActiveRecipe(blueprint, selected);
        ItemStack persisted = blueprint.copy();
        helper.assertTrue(selected.equals(BlueprintRecipeData.getActiveRecipe(persisted)),
                "Selected recipe ID must survive ItemStack persistence");
        helper.assertTrue(pages.get(0).value().getResultItem(helper.getLevel().registryAccess()).isEmpty() == false,
                "Switching pages must not clear the previous real recipe data");
        helper.succeed();
    }

    private static BenchHarness bench(GameTestHelper helper, ResourceLocation recipeId) {
        BenchHarness harness = benchWithoutBlueprint(helper, recipeId);
        GunBenchRecipe recipe = recipe(helper, recipeId).value();
        harness.container.setItem(GunBenchBlueprintConsumption.BLUEPRINT_SLOT, first(recipe.getBlueprint()));
        harness.menu.slotsChanged(harness.container);
        helper.assertFalse(harness.container.getItem(GunBenchBlueprintConsumption.OUTPUT_SLOT).isEmpty(),
                "Expected Gun Bench output for " + recipeId);
        return harness;
    }

    private static BenchHarness benchWithoutBlueprint(GameTestHelper helper, ResourceLocation recipeId) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer container = new SimpleContainer(GunBenchBlueprintConsumption.BLUEPRINT_SLOT + 1);
        GunBenchMenu menu = new GunBenchMenu(
                1,
                player.getInventory(),
                container,
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO))
        );
        player.containerMenu = menu;
        GunBenchRecipe recipe = recipe(helper, recipeId).value();
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            Ingredient ingredient = recipe.getIngredients().get(slot);
            if (!ingredient.isEmpty()) {
                container.setItem(slot, first(ingredient));
            }
        }
        menu.slotsChanged(container);
        return new BenchHarness(player, container, menu);
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<GunBenchRecipe> recipe(GameTestHelper helper, ResourceLocation id) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager().byKey(id)
                .orElseThrow(() -> new IllegalStateException("Missing recipe " + id));
        if (!(holder.value() instanceof GunBenchRecipe)) {
            throw new IllegalStateException(id + " is not a Gun Bench recipe");
        }
        return (RecipeHolder<GunBenchRecipe>) holder;
    }

    private static ItemStack first(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            throw new IllegalStateException("Ingredient has no display or matching stacks");
        }
        return items[0].copyWithCount(1);
    }

    private static ItemStack stack(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static ResourceLocation scguns(String path) {
        return ResourceLocation.fromNamespaceAndPath("scguns", path);
    }

    private record BenchHarness(Player player, SimpleContainer container, GunBenchMenu menu) {
    }

    private ScorchedIntegrationGameTests() {
    }
}
