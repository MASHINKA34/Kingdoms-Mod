package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SplashingNuggetGameTests {
    private static final int ROLLS = 2_000;
    private static final Set<String> ORE_DERIVED_NUGGET_SOURCES = Set.of(
            "create:splashing/crushed_raw_iron",
            "create:splashing/crushed_raw_gold",
            "scguns:create/treated_iron_blend_splashing"
    );

    @GameTest(template = "empty", batch = "splashing_nuggets", timeoutTicks = 400)
    public static void renewableSplashingSourcesDropNoNuggets(GameTestHelper helper) {
        assertStripped(helper, Items.GRAVEL, Items.FLINT);
        assertStripped(helper, Items.RED_SAND, Items.DEAD_BUSH);
        assertStripped(helper, Items.SOUL_SAND, Items.QUARTZ);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "splashing_nuggets", timeoutTicks = 400)
    public static void crushedOreSplashingKeepsItsNuggets(GameTestHelper helper) {
        assertYields(helper, item("create:crushed_raw_iron"), Items.IRON_NUGGET);
        assertYields(helper, item("create:crushed_raw_gold"), Items.GOLD_NUGGET);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "splashing_nuggets", timeoutTicks = 200)
    public static void onlyOreDerivedSplashingRecipesStillListNuggets(GameTestHelper helper) {
        RecipeType<?> splashing = AllRecipeTypes.SPLASHING.getType();
        List<String> offenders = new ArrayList<>();
        for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
            if (holder.value().getType() != splashing || !(holder.value() instanceof ProcessingRecipe<?, ?> processing)) {
                continue;
            }
            boolean yieldsNugget = processing.getRollableResultsAsItemStacks().stream()
                    .anyMatch(stack -> stack.is(Items.IRON_NUGGET) || stack.is(Items.GOLD_NUGGET));
            if (yieldsNugget && !ORE_DERIVED_NUGGET_SOURCES.contains(holder.id().toString())) {
                offenders.add(holder.id().toString());
            }
        }
        helper.assertTrue(offenders.isEmpty(), "splashing still hands out vanilla nuggets from " + offenders);
        helper.succeed();
    }

    private static void assertStripped(GameTestHelper helper, Item input, Item kept) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(
                AllFanProcessingTypes.SPLASHING.canProcess(new ItemStack(input), level),
                input + " lost its splashing recipe entirely"
        );

        boolean sawKept = false;
        for (int roll = 0; roll < ROLLS; roll++) {
            for (ItemStack result : splash(level, input)) {
                helper.assertFalse(result.is(Items.IRON_NUGGET), input + " still washes into iron nuggets");
                helper.assertFalse(result.is(Items.GOLD_NUGGET), input + " still washes into gold nuggets");
                sawKept |= result.is(kept);
            }
        }
        helper.assertTrue(sawKept, input + " no longer washes into " + kept);
    }

    private static void assertYields(GameTestHelper helper, Item input, Item expected) {
        ServerLevel level = helper.getLevel();
        boolean sawExpected = false;
        for (int roll = 0; roll < 64 && !sawExpected; roll++) {
            for (ItemStack result : splash(level, input)) {
                sawExpected |= result.is(expected);
            }
        }
        helper.assertTrue(sawExpected, input + " no longer washes into " + expected);
    }

    private static List<ItemStack> splash(ServerLevel level, Item input) {
        List<ItemStack> results = AllFanProcessingTypes.SPLASHING.process(new ItemStack(input), level);
        return results == null ? List.of() : results;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private SplashingNuggetGameTests() {
    }
}
