package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.scorched.DisabledRaidFlares;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "top.ribs.scguns.item.FlarePistolItem", remap = false)
public abstract class ScorchedFlarePistolMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdoms$blockRaidFlare(
            Level level,
            Player player,
            InteractionHand hand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback
    ) {
        if (!DisabledRaidFlares.isDisabled(player.getOffhandItem())) {
            return;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("kingdoms.scguns.raid_flare.disabled")
                    .withStyle(ChatFormatting.RED), true);
        }
        callback.setReturnValue(InteractionResultHolder.fail(player.getItemInHand(hand)));
    }
}
