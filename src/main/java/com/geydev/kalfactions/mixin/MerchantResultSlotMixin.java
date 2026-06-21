package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.faction.VillagerTradeRewards;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
    @Shadow
    @Final
    private Merchant merchant;

    @Inject(method = "onTake", at = @At("TAIL"))
    private void kingdoms$onVillagerTrade(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && this.merchant instanceof Villager) {
            VillagerTradeRewards.onVillagerTrade(serverPlayer, stack);
        }
    }
}
