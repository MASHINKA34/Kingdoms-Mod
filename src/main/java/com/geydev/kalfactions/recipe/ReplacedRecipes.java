package com.geydev.kalfactions.recipe;

import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public final class ReplacedRecipes {
    public static final List<ResourceLocation> RECIPE_IDS = List.of(
            ResourceLocation.fromNamespaceAndPath("scguns", "gun_bench")
    );
    private static final Set<ResourceLocation> RECIPE_ID_SET = Set.copyOf(RECIPE_IDS);

    public static boolean isReplaced(RecipeHolder<?> holder) {
        return RECIPE_ID_SET.contains(holder.id());
    }

    public static List<RecipeHolder<?>> removeRecipes(Iterable<RecipeHolder<?>> recipes) {
        return StreamSupport.stream(recipes.spliterator(), false)
                .filter(holder -> !isReplaced(holder))
                .toList();
    }

    private ReplacedRecipes() {
    }
}
