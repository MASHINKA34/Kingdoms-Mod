package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.faction.FactionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void kingdoms$factionDiscount(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int levels = FactionManager.get(serverPlayer.serverLevel())
                .getFactionForMember(serverPlayer.getUUID())
                .map(faction -> faction.researchBonusCount("VILLAGER_DISCOUNT"))
                .orElse(0);
        if (levels <= 0) {
            return;
        }
        double discount = Math.min(0.90D, 0.10D * levels);
        Villager self = (Villager) (Object) this;
        for (MerchantOffer offer : self.getOffers()) {
            int cost = offer.getCostA().getCount();
            offer.addToSpecialPriceDiff(-Math.max(1, Mth.floor(cost * discount)));
        }
    }
}
