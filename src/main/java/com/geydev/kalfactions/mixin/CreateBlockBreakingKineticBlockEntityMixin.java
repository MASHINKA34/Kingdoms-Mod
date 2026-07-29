package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBreakingKineticBlockEntity.class)
public abstract class CreateBlockBreakingKineticBlockEntityMixin {
    @Shadow
    protected abstract BlockPos getBreakingPos();

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryFromStationaryBreakers(
            BlockState state,
            float hardness,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockBreakingKineticBlockEntity blockEntity =
                (BlockBreakingKineticBlockEntity) (Object) this;
        Level level = blockEntity.getLevel();
        if (level != null
                && !MachineProtection.canContraptionBreak(
                        level,
                        getBreakingPos(),
                        blockEntity.getBlockPos()
                )) {
            cir.setReturnValue(false);
        }
    }
}
