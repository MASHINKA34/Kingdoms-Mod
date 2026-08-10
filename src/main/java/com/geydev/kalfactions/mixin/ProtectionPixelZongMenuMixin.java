package com.geydev.kalfactions.mixin;

import net.mcreator.protectionpixel.procedures.ZongduringProcedure;
import net.mcreator.protectionpixel.world.inventory.ZongMenu;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ZongMenu.class)
public abstract class ProtectionPixelZongMenuMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void kingdoms$keepPowerSlotsBeforeClear(Player player, CallbackInfo callback) {
        if (player != null) {
            ZongduringProcedure.execute(player);
        }
    }
}
