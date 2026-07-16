package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class BlockBreakingMovementBehaviourMixin {
    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdoms$guardVisit(MovementContext context, BlockPos pos, CallbackInfo callback) {
        if (kingdoms$blocked(context, pos)) {
            callback.cancel();
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdoms$guardDestroy(MovementContext context, BlockPos breakingPos, CallbackInfo callback) {
        if (kingdoms$blocked(context, breakingPos)) {
            callback.cancel();
        }
    }

    private static boolean kingdoms$blocked(MovementContext context, BlockPos pos) {
        if (context == null || context.world == null) {
            return false;
        }
        BlockPos anchor = context.contraption == null ? null : context.contraption.anchor;
        return !MachineProtection.canContraptionBreak(context.world, pos, anchor);
    }
}
