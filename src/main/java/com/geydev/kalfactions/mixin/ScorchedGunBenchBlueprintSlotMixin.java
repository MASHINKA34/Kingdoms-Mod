package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.sculk.SculkTomePolicy;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "top.ribs.scguns.client.screen.GunBenchMenu$1", remap = false)
public abstract class ScorchedGunBenchBlueprintSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdoms$allowSculkTome(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (SculkTomePolicy.isTome(stack)) {
            callback.setReturnValue(true);
        }
    }
}
