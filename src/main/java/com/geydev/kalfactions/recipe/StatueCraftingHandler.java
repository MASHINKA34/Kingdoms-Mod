package com.geydev.kalfactions.recipe;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class StatueCraftingHandler {
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide() || !(event.getInventory() instanceof CraftingContainer container)) {
            return;
        }
        CraftingInput.Positioned positioned = container.asPositionedCraftInput();
        level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, positioned.input(), level)
                .map(RecipeHolder::value)
                .filter(StatueCraftingRecipe.class::isInstance)
                .map(StatueCraftingRecipe.class::cast)
                .ifPresent(recipe -> consumeExtraItems(container, positioned, recipe));
    }

    private static void consumeExtraItems(
            CraftingContainer container, CraftingInput.Positioned positioned, StatueCraftingRecipe recipe) {
        List<SizedIngredient> ingredients = recipe.sizedIngredients();
        CraftingInput input = positioned.input();
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int extra = ingredients.get(x + y * input.width()).count() - 1;
                if (extra > 0) {
                    container.removeItem(x + positioned.left() + (y + positioned.top()) * container.getWidth(), extra);
                }
            }
        }
    }

    private StatueCraftingHandler() {
    }
}
