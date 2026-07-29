package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBreakingMovementBehaviour.class)
public abstract class CreateBlockBreakingMovementBehaviourMixin {
    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryFromMovingBreakers(
            Level level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!MachineProtection.canContraptionBreak(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
