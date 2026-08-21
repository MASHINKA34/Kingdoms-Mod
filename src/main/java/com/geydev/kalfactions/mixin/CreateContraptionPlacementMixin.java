package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Contraption.class)
public abstract class CreateContraptionPlacementMixin {
    @Inject(method = "customBlockPlacement", at = @At("HEAD"), cancellable = true)
    private void kingdoms$keepProtectedBlocksOnDisassembly(
            LevelAccessor world,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!MachineProtection.protectsBlocks(world, pos)
                || MachineProtection.sharesProtectedZone(world, pos, ((Contraption) (Object) this).anchor)) {
            return;
        }
        if (!world.isClientSide()) {
            Block.dropResources(state, world, pos, null);
        }
        cir.setReturnValue(true);
    }
}
