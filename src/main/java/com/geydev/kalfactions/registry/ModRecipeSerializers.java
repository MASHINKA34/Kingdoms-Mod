package com.geydev.kalfactions.registry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.recipe.StatueCraftingRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, KalFactions.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, StatueCraftingRecipeSerializer> STATUE_CRAFTING =
            SERIALIZERS.register("statue_crafting", StatueCraftingRecipeSerializer::new);

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    private ModRecipeSerializers() {
    }
}
