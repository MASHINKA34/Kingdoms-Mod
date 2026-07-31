package com.geydev.kalfactions.scorched;

import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.ribs.scguns.client.screen.GunBenchRecipe;

public final class GunBenchBlueprintConsumption {
    public static final int OUTPUT_SLOT = 10;
    public static final int BLUEPRINT_SLOT = 11;
    public static final List<ResourceLocation> BLUEPRINT_IDS = List.of(
            scguns("iron_blueprint"),
            scguns("wrecker_blueprint"),
            scguns("treated_brass_blueprint"),
            scguns("diamond_steel_blueprint"),
            scguns("ocean_blueprint"),
            scguns("piglin_blueprint"),
            scguns("deep_dark_blueprint"),
            scguns("end_blueprint"),
            scguns("scorched_blueprint"),
            scguns("exo_suit_blueprint")
    );
    private static final Set<ResourceLocation> BLUEPRINT_ID_SET = Set.copyOf(BLUEPRINT_IDS);

    public static boolean isConsumableBlueprint(ItemStack stack) {
        return !stack.isEmpty() && BLUEPRINT_ID_SET.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean hasMatchingRecipe(Level level, Container container) {
        if (!isConsumableBlueprint(container.getItem(BLUEPRINT_SLOT))) {
            return false;
        }
        SimpleContainer snapshot = new SimpleContainer(BLUEPRINT_SLOT + 1);
        for (int slot = 0; slot <= BLUEPRINT_SLOT; slot++) {
            snapshot.setItem(slot, container.getItem(slot).copy());
        }
        return level.getRecipeManager().getRecipeFor(
                GunBenchRecipe.Type.INSTANCE,
                new GunBenchRecipe.Input(snapshot),
                level
        ).isPresent();
    }

    public static boolean mayTakeResult(Level level, Container container) {
        ItemStack blueprint = container.getItem(BLUEPRINT_SLOT);
        return !isConsumableBlueprint(blueprint) || hasMatchingRecipe(level, container);
    }

    public static boolean consumeOne(Container container) {
        ItemStack blueprint = container.getItem(BLUEPRINT_SLOT);
        if (!isConsumableBlueprint(blueprint)) {
            return false;
        }
        blueprint.shrink(1);
        container.setItem(BLUEPRINT_SLOT, blueprint);
        container.setChanged();
        return true;
    }

    private static ResourceLocation scguns(String path) {
        return ResourceLocation.fromNamespaceAndPath("scguns", path);
    }

    private GunBenchBlueprintConsumption() {
    }
}
