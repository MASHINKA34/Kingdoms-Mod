package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeIngredientGameTests {
    @GameTest(template = "empty")
    public static void noRecipeHasAnUnresolvableIngredient(GameTestHelper helper) {
        List<String> broken = new ArrayList<>();
        for (RecipeHolder<?> holder : helper.getLevel().getRecipeManager().getRecipes()) {
            for (Ingredient ingredient : holder.value().getIngredients()) {
                if (!ingredient.isEmpty() && ingredient.hasNoItems()) {
                    broken.add(holder.id().toString());
                    break;
                }
            }
        }
        helper.assertTrue(broken.isEmpty(), "Recipes with an ingredient that matches nothing: " + broken);
        helper.succeed();
    }
}
