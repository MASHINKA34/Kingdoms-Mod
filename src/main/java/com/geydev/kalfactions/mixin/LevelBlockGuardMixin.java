package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelBlockGuardMixin {
    @Inject(method = "removeBlock", at = @At("HEAD"), cancellable = true)
    private void kingdoms$guardRemoveBlock(BlockPos pos, boolean isMoving, CallbackInfoReturnable<Boolean> cir) {
        if (MachineProtection.blocksProjectileGrief((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kingdoms$guardDestroyBlock(
            BlockPos pos,
            boolean dropBlock,
            Entity entity,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (MachineProtection.blocksProjectileGrief((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kingdoms$guardSetBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (MachineProtection.blocksProjectileGrief((Level) (Object) this, pos)) {
            cir.setReturnValue(false);
        }
    }
}
