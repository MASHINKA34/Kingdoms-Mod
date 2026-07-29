package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.contraptions.actors.plough.PloughMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PloughMovementBehaviour.class)
public abstract class CreatePloughMovementBehaviourMixin {
    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryFromPlough(
            MovementContext context,
            BlockPos pos,
            CallbackInfo ci
    ) {
        BlockPos anchor = context.contraption.entity == null
                ? null
                : context.contraption.entity.blockPosition();
        if (!MachineProtection.canContraptionBreak(context.world, pos, anchor)
                || !MachineProtection.canContraptionBreak(context.world, pos.below(), anchor)) {
            ci.cancel();
        }
    }
}
