package com.geydev.kalfactions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "top.ribs.scguns.client.screen.BlueprintScreen", remap = false)
public abstract class ScorchedBlueprintScreenMixin {
    @Shadow
    private void loadAvailableRecipes() {
    }

    @Shadow
    private void loadActiveRecipeAsCurrentPage() {
    }

    @Inject(method = "init", at = @At("HEAD"), remap = false)
    private void kingdoms$reloadSyncedRecipes(CallbackInfo callback) {
        loadAvailableRecipes();
        loadActiveRecipeAsCurrentPage();
    }
}
