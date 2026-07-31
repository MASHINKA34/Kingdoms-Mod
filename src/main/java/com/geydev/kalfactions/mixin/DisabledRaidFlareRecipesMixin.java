package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.scorched.DisabledRaidFlares;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class DisabledRaidFlareRecipesMixin {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Inject(method = "apply", at = @At("TAIL"))
    private void kingdoms$removeRaidFlareRecipes(
            java.util.Map<?, ?> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback
    ) {
        RecipeManager manager = (RecipeManager) (Object) this;
        List<RecipeHolder<?>> filtered = DisabledRaidFlares.removeRecipes(manager.getRecipes(), registries);
        int removed = manager.getRecipes().size() - filtered.size();
        if (removed > 0) {
            manager.replaceRecipes(filtered);
            KalFactions.LOGGER.info("Disabled {} Scorched Guns raid flare recipes", removed);
        }
    }
}
