package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class BlockBreakingKineticBlockEntityMixin {
    @Shadow
    protected BlockPos breakingPos;

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdoms$guardCanBreak(
            BlockState stateToBreak,
            float blockHardness,
            CallbackInfoReturnable<Boolean> callback
    ) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || breakingPos == null) {
            return;
        }
        if (!MachineProtection.canContraptionBreak(self.getLevel(), breakingPos, self.getBlockPos())) {
            callback.setReturnValue(false);
        }
    }
}
