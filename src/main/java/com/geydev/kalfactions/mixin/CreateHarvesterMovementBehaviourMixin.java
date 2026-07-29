package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HarvesterMovementBehaviour.class)
public abstract class CreateHarvesterMovementBehaviourMixin {
    @Inject(method = "visitNewPosition", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryFromHarvester(
            MovementContext context,
            BlockPos pos,
            CallbackInfo ci
    ) {
        BlockPos anchor = context.contraption.entity == null
                ? null
                : context.contraption.entity.blockPosition();
        if (!MachineProtection.canContraptionBreak(context.world, pos, anchor)) {
            ci.cancel();
        }
    }
}
