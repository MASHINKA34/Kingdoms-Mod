package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.foundation.utility.AbstractBlockBreakQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlockBreakQueue.class)
public abstract class CreateBlockBreakQueueMixin {
    @Inject(method = "makeCallbackFor", at = @At("RETURN"), cancellable = true)
    private void kingdoms$protectQuarryFromBreakQueues(
            Level level,
            float effectChance,
            ItemStack tool,
            @Nullable Player player,
            BiConsumer<BlockPos, ItemStack> drop,
            CallbackInfoReturnable<Consumer<BlockPos>> cir
    ) {
        Consumer<BlockPos> original = cir.getReturnValue();
        cir.setReturnValue(pos -> {
            if (MachineProtection.canContraptionBreak(level, pos)) {
                original.accept(pos);
            }
        });
    }
}
