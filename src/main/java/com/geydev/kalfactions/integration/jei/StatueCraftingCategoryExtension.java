package com.geydev.kalfactions.integration.jei;

import com.geydev.kalfactions.recipe.StatueCraftingRecipe;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public final class StatueCraftingCategoryExtension implements ICraftingCategoryExtension<StatueCraftingRecipe> {
    @Override
    public void setRecipe(
            RecipeHolder<StatueCraftingRecipe> holder,
            IRecipeLayoutBuilder builder,
            ICraftingGridHelper craftingGridHelper,
            IFocusGroup focuses) {
        StatueCraftingRecipe recipe = holder.value();
        List<List<ItemStack>> inputs = recipe.sizedIngredients().stream()
                .map(SizedIngredient::getItems)
                .map(List::of)
                .toList();
        craftingGridHelper.createAndSetInputs(builder, inputs, StatueCraftingRecipe.WIDTH, StatueCraftingRecipe.HEIGHT);
        craftingGridHelper.createAndSetOutputs(builder, List.of(recipe.result()));
    }

    @Override
    public int getWidth(RecipeHolder<StatueCraftingRecipe> holder) {
        return StatueCraftingRecipe.WIDTH;
    }

    @Override
    public int getHeight(RecipeHolder<StatueCraftingRecipe> holder) {
        return StatueCraftingRecipe.HEIGHT;
    }
}
