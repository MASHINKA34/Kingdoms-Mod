package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.profiling.LagTaxHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {
    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
    private void kingdoms$beginTick(CallbackInfo callback) {
        if (LagTaxHooks.shouldSkipTick(blockEntity)) {
            callback.cancel();
            return;
        }
        LagTaxHooks.beginTick(blockEntity);
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void kingdoms$endTick(CallbackInfo callback) {
        LagTaxHooks.endTick(blockEntity);
    }
}
