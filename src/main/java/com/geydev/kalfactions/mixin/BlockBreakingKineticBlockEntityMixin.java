package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.foundation.utility.BlockHelper;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Redirect(
            method = "onBlockBroken",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/BlockHelper;destroyBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FLjava/util/function/Consumer;)V"
            ),
            remap = false
    )
    private void kingdoms$consumeTrackedOre(
            Level level,
            BlockPos pos,
            float effectChance,
            Consumer<ItemStack> dropConsumer
    ) {
        if (level instanceof ServerLevel serverLevel
                && ResourceClusterManager.get(serverLevel).consumeTrackedOre(serverLevel, pos)
                == ResourceClusterManager.OreConsumption.DEPLETED_NO_DROP) {
            return;
        }
        BlockHelper.destroyBlock(level, pos, effectChance, dropConsumer);
    }
}
