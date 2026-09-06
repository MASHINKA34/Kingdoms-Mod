package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.KalFactions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class CraftBonusPolicy {
    private static RecipeManager cachedRecipes;
    private static Set<Item> reversibleItems = Set.of();

    public static boolean allows(ServerLevel level, ItemStack result) {
        if (result.isEmpty()
                || result.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                        .nonEmptyItems().iterator().hasNext()
                || !result.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty()) {
            return false;
        }
        if (cachedRecipes != level.getRecipeManager()) {
            rebuild(level);
        }
        return !reversibleItems.contains(result.getItem());
    }

    private static void rebuild(ServerLevel level) {
        Map<Item, Map<Item, Conversion>> conversions = new HashMap<>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            var recipe = holder.value();
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || recipe.isSpecial()) {
                continue;
            }
            List<Ingredient> inputs = recipe.getIngredients().stream()
                    .filter(ingredient -> !ingredient.isEmpty()).toList();
            if (inputs.isEmpty()) {
                continue;
            }
            for (ItemStack candidate : inputs.getFirst().getItems()) {
                if (candidate.isEmpty() || inputs.stream().anyMatch(ingredient -> !ingredient.test(candidate))) {
                    continue;
                }
                conversions.computeIfAbsent(candidate.getItem(), ignored -> new HashMap<>())
                        .merge(output.getItem(), new Conversion(inputs.size(), output.getCount()),
                                Conversion::moreProductive);
            }
        }
        Set<Item> blocked = new HashSet<>();
        for (var from : conversions.entrySet()) {
            for (var to : from.getValue().entrySet()) {
                Conversion reverse = conversions.getOrDefault(to.getKey(), Map.of()).get(from.getKey());
                if (reverse != null && to.getValue().reversibleWith(reverse)) {
                    blocked.add(from.getKey());
                    blocked.add(to.getKey());
                }
            }
        }
        reversibleItems = Set.copyOf(blocked);
        cachedRecipes = level.getRecipeManager();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            clear();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    private static void clear() {
        cachedRecipes = null;
        reversibleItems = Set.of();
    }

    private record Conversion(int consumed, int produced) {
        private Conversion moreProductive(Conversion other) {
            return (long) produced * other.consumed >= (long) other.produced * consumed ? this : other;
        }

        private boolean reversibleWith(Conversion other) {
            return (long) produced * other.produced >= (long) consumed * other.consumed;
        }
    }

    private CraftBonusPolicy() {
    }
}
