package com.geydev.kalfactions.integration.jei;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.recipe.StatueCraftingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class KingdomsJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(StatueCraftingRecipe.class, new StatueCraftingCategoryExtension());
    }
}
