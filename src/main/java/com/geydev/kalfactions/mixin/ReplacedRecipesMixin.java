package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.recipe.ReplacedRecipes;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class ReplacedRecipesMixin {
    @Inject(method = "apply", at = @At("TAIL"))
    private void kingdoms$removeReplacedRecipes(
            java.util.Map<?, ?> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        RecipeManager manager = (RecipeManager) (Object) this;
        List<RecipeHolder<?>> filtered = ReplacedRecipes.removeRecipes(manager.getRecipes());
        int removed = manager.getRecipes().size() - filtered.size();
        if (removed > 0) {
            manager.replaceRecipes(filtered);
            KalFactions.LOGGER.info("Removed {} recipes replaced by Kingdoms variants", removed);
        }
    }
}
