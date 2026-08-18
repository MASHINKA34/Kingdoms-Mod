package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Contraption.class)
public abstract class CreateContraptionMixin {
    @Inject(method = "moveBlock", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryFromContraptionAssembly(
            Level level,
            @Nullable Direction direction,
            Queue<BlockPos> frontier,
            Set<BlockPos> visited,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos pos = frontier.peek();
        if (pos == null) {
            return;
        }
        if (MachineProtection.protectsBlocks(level, pos)) {
            visited.add(frontier.poll());
            cir.setReturnValue(true);
            return;
        }
        if (!MachineProtection.canContraptionAct(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
