package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.item.LegacyTokenConversion;
import com.geydev.kalfactions.registry.ModItems;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MinibossTokenRecipeGameTests {
    private static final ResourceLocation GUN_BENCH = ResourceLocation.fromNamespaceAndPath("scguns", "gun_bench");
    private static final ResourceLocation PLATFORM =
            ResourceLocation.fromNamespaceAndPath("protection_pixel", "armorloadplatform");

    @GameTest(template = "empty", batch = "miniboss_token_recipes")
    public static void gunBenchIsOnlyCraftableWithTheLushCavesToken(GameTestHelper helper) {
        if (!ModList.get().isLoaded("scguns")) {
            helper.succeed();
            return;
        }
        RecipeHolder<?> holder = assertSingleRecipeUsesToken(
                helper,
                GUN_BENCH,
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "gun_bench"),
                ModItems.MINIBOSS_TOKEN_LUSH_CAVES.get()
        );
        ItemStack token = new ItemStack(ModItems.MINIBOSS_TOKEN_LUSH_CAVES.get());
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        ItemStack flint = new ItemStack(Items.FLINT);
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);

        assertAssembles(helper, holder, 3, 2, List.of(
                iron, flint, token,
                planks, planks, ItemStack.EMPTY
        ), true);
        assertAssembles(helper, holder, 3, 2, List.of(
                iron, flint, ItemStack.EMPTY,
                planks, planks, ItemStack.EMPTY
        ), false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniboss_token_recipes")
    public static void armorPlatformIsOnlyCraftableWithTheGhostToken(GameTestHelper helper) {
        if (!ModList.get().isLoaded("protection_pixel") || !ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        RecipeHolder<?> holder = assertSingleRecipeUsesToken(
                helper,
                PLATFORM,
                ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "armor_load_platform"),
                ModItems.MINIBOSS_TOKEN_GHOST.get()
        );
        ItemStack token = new ItemStack(ModItems.MINIBOSS_TOKEN_GHOST.get());
        ItemStack alloy = create("andesite_alloy");
        ItemStack cog = create("cogwheel");
        ItemStack casing = create("brass_casing");
        ItemStack largeCog = create("large_cogwheel");

        assertMatches(helper, holder, mechanicalInput(5, 3, List.of(
                alloy, cog, token, cog, alloy,
                alloy, cog, ItemStack.EMPTY, cog, alloy,
                ItemStack.EMPTY, casing, largeCog, casing, ItemStack.EMPTY
        )), true);
        assertMatches(helper, holder, mechanicalInput(5, 3, List.of(
                alloy, cog, ItemStack.EMPTY, cog, alloy,
                alloy, cog, ItemStack.EMPTY, cog, alloy,
                ItemStack.EMPTY, casing, largeCog, casing, ItemStack.EMPTY
        )), false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniboss_token_recipes")
    public static void theRetiredEndTokenTurnsIntoTheGenericToken(GameTestHelper helper) {
        ItemStack legacy = new ItemStack(ModItems.MINIBOSS_TOKEN_END.get(), 7);
        ItemStack replacement = LegacyTokenConversion.replacementFor(legacy);

        helper.assertTrue(replacement.is(ModItems.MINIBOSS_TOKEN.get()), "legacy token is converted");
        helper.assertValueEqual(replacement.getCount(), 7, "converted stack size");
        helper.assertTrue(
                LegacyTokenConversion.replacementFor(new ItemStack(ModItems.MINIBOSS_TOKEN.get())).isEmpty(),
                "generic token is left alone"
        );
        helper.assertValueEqual(
                ModItems.MINIBOSS_TOKEN_END.get().getDescriptionId(),
                ModItems.MINIBOSS_TOKEN.get().getDescriptionId(),
                "retired token name"
        );
        helper.succeed();
    }

    private static RecipeHolder<?> assertSingleRecipeUsesToken(
            GameTestHelper helper,
            ResourceLocation result,
            ResourceLocation expectedRecipe,
            Item token
    ) {
        RecipeManager manager = helper.getLevel().getRecipeManager();
        List<ResourceLocation> found = new ArrayList<>();
        for (RecipeHolder<?> candidate : manager.getRecipes()) {
            ItemStack output = candidate.value().getResultItem(helper.getLevel().registryAccess());
            if (!output.isEmpty() && result.equals(BuiltInRegistries.ITEM.getKey(output.getItem()))) {
                found.add(candidate.id());
            }
        }
        helper.assertValueEqual(found, List.of(expectedRecipe), "recipes for " + result);

        RecipeHolder<?> holder = manager.byKey(expectedRecipe).orElseThrow();
        long tokens = holder.value().getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty() && ingredient.test(new ItemStack(token)))
                .count();
        helper.assertValueEqual(tokens, 1L, "token ingredients in " + expectedRecipe);
        return holder;
    }

    private static void assertAssembles(
            GameTestHelper helper,
            RecipeHolder<?> holder,
            int width,
            int height,
            List<ItemStack> grid,
            boolean expected
    ) {
        assertMatches(helper, holder, CraftingInput.of(width, height, grid), expected);
    }

    @SuppressWarnings("unchecked")
    private static void assertMatches(
            GameTestHelper helper,
            RecipeHolder<?> holder,
            CraftingInput input,
            boolean expected
    ) {
        Recipe<CraftingInput> recipe = (Recipe<CraftingInput>) holder.value();
        helper.assertValueEqual(
                recipe.matches(input, helper.getLevel()),
                expected,
                (expected ? "assembles " : "must not assemble without the token: ") + holder.id()
        );
    }

    private static CraftingInput mechanicalInput(int width, int height, List<ItemStack> grid) {
        try {
            Constructor<?> constructor = Class
                    .forName("com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput")
                    .getDeclaredConstructor(int.class, int.class, List.class);
            constructor.setAccessible(true);
            return (CraftingInput) constructor.newInstance(width, height, grid);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Create mechanical crafting input is unavailable", exception);
        }
    }

    private static ItemStack create(String path) {
        return new ItemStack(BuiltInRegistries.ITEM
                .get(ResourceLocation.fromNamespaceAndPath("create", path)));
    }
}
