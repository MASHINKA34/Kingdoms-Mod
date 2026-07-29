package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockHelper.class)
public abstract class CreateBlockHelperMixin {
    @Inject(method = "placeSchematicBlock", at = @At("HEAD"), cancellable = true)
    private static void kingdoms$protectQuarryFromSchematicPlacement(
            Level level,
            BlockState state,
            BlockPos pos,
            ItemStack stack,
            @Nullable CompoundTag data,
            CallbackInfo ci
    ) {
        if (!MachineProtection.canContraptionBreak(level, pos)) {
            ci.cancel();
        }
    }
}
