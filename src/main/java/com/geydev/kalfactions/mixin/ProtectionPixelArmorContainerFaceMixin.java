package com.geydev.kalfactions.mixin;

import javax.annotation.Nullable;
import net.mcreator.protectionpixel.block.entity.ArmorhangerBlockEntity;
import net.mcreator.protectionpixel.block.entity.ArmorloadplatformBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ ArmorloadplatformBlockEntity.class, ArmorhangerBlockEntity.class })
public abstract class ProtectionPixelArmorContainerFaceMixin {
    @Inject(method = "canPlaceItemThroughFace", at = @At("HEAD"), cancellable = true)
    private void kingdoms$rejectAutomatedInsertion(
            int index,
            ItemStack stack,
            @Nullable Direction direction,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(false);
    }
}
