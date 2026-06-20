package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceType;
import java.util.Optional;
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
        if (!(player instanceof ServerPlayer serverPlayer) || !(this.merchant instanceof Villager)) {
            return;
        }
        FactionManager manager = FactionManager.get(serverPlayer.serverLevel());
        Optional<Faction> factionOpt = manager.getFactionForMember(serverPlayer.getUUID());
        if (factionOpt.isEmpty()) {
            return;
        }
        Faction faction = factionOpt.get();
        manager.addInfluence(faction.id(), InfluenceType.ECONOMIC, 1L);
        int extra = faction.researchBonusCount("VILLAGER_EXTRA");
        if (extra > 0 && !stack.isEmpty()) {
            double chance = Math.min(0.60D, 0.25D * extra);
            if (serverPlayer.serverLevel().getRandom().nextDouble() < chance) {
                ItemStack bonus = stack.copy();
                if (!serverPlayer.getInventory().add(bonus)) {
                    serverPlayer.drop(bonus, false);
                }
            }
        }
    }
}
