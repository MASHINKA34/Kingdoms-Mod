package com.geydev.kalfactions.recipe;

import com.geydev.kalfactions.registry.ModRecipeSerializers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public final class StatueCraftingRecipe implements CraftingRecipe {
    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;
    public static final int SIZE = WIDTH * HEIGHT;

    private final CraftingBookCategory category;
    private final List<String> pattern;
    private final Map<Character, SizedIngredient> key;
    private final ItemStack result;
    private final List<SizedIngredient> ingredients;

    public StatueCraftingRecipe(
            CraftingBookCategory category,
            List<String> pattern,
            Map<Character, SizedIngredient> key,
            ItemStack result) {
        this.category = category;
        this.pattern = List.copyOf(pattern);
        this.key = Map.copyOf(key);
        this.result = result;
        this.ingredients = resolve(this.pattern, this.key);
    }

    private static List<SizedIngredient> resolve(List<String> pattern, Map<Character, SizedIngredient> key) {
        if (pattern.size() != HEIGHT) {
            throw new IllegalArgumentException("Statue recipe pattern must have exactly " + HEIGHT + " rows");
        }
        List<SizedIngredient> resolved = new ArrayList<>(SIZE);
        for (String row : pattern) {
            if (row.length() != WIDTH) {
                throw new IllegalArgumentException("Statue recipe pattern rows must be exactly " + WIDTH + " symbols long");
            }
            for (int x = 0; x < WIDTH; x++) {
                char symbol = row.charAt(x);
                SizedIngredient ingredient = key.get(symbol);
                if (ingredient == null) {
                    throw new IllegalArgumentException("Statue recipe pattern uses undefined symbol '" + symbol + "'");
                }
                resolved.add(ingredient);
            }
        }
        return List.copyOf(resolved);
    }

    public List<String> pattern() {
        return pattern;
    }

    public Map<Character, SizedIngredient> key() {
        return key;
    }

    public ItemStack result() {
        return result;
    }

    public List<SizedIngredient> sizedIngredients() {
        return ingredients;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != WIDTH || input.height() != HEIGHT) {
            return false;
        }
        for (int slot = 0; slot < SIZE; slot++) {
            if (!ingredients.get(slot).test(input.getItem(slot))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= WIDTH && height >= HEIGHT;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> plain = NonNullList.withSize(SIZE, Ingredient.EMPTY);
        for (int slot = 0; slot < SIZE; slot++) {
            plain.set(slot, ingredients.get(slot).ingredient());
        }
        return plain;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.STATUE_CRAFTING.get();
    }
}
