package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.faction.FactionManager;
import java.util.ArrayList;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Shadow
    @Final
    private Container enchantSlots;

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void kingdoms$boostEnchant(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int levels = FactionManager.get(serverPlayer.serverLevel())
                .getFactionForMember(serverPlayer.getUUID())
                .map(faction -> faction.researchBonusCount("ENCHANT_BOOST"))
                .orElse(0);
        if (levels <= 0) {
            return;
        }
        ItemStack result = this.enchantSlots.getItem(0);
        if (result.isEmpty()) {
            return;
        }
        RandomSource random = serverPlayer.serverLevel().getRandom();
        EnchantmentHelper.updateEnchantments(result, mutable -> {
            for (Holder<Enchantment> holder : new ArrayList<>(mutable.keySet())) {
                if (random.nextFloat() < 0.5F) {
                    mutable.set(holder, mutable.getLevel(holder) + 1);
                }
            }
        });
    }
}
