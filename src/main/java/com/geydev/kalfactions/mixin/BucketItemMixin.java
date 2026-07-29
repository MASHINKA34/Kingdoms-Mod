package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.quarry.QuarryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin extends Item {
    @Shadow
    @Final
    private Fluid content;

    protected BucketItemMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryBucketUse(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
            return;
        }
        ClipContext.Fluid fluidMode = content == Fluids.EMPTY
                ? ClipContext.Fluid.SOURCE_ONLY
                : ClipContext.Fluid.NONE;
        BlockHitResult hit = getPlayerPOVHitResult(level, player, fluidMode);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Direction direction = hit.getDirection();
        QuarryManager manager = QuarryManager.get(serverLevel);
        if (manager.isQuarry(serverLevel, pos)
                || manager.isQuarry(serverLevel, pos.relative(direction))) {
            cir.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
        }
    }

    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void kingdoms$protectQuarryBucketPlacement(
            @Nullable Player entity,
            Level level,
            BlockPos pos,
            @Nullable BlockHitResult hitResult,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || entity instanceof ServerPlayer player && player.hasPermissions(2)) {
            return;
        }
        if (QuarryManager.get(serverLevel).isQuarry(serverLevel, pos)) {
            cir.setReturnValue(false);
        }
    }
}
