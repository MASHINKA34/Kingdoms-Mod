package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.sculk.SculkTomePolicy;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "top.ribs.scguns.client.screen.GunBenchMenu$2", remap = false)
public abstract class ScorchedGunBenchResultSlotMixin extends Slot {
    private ScorchedGunBenchResultSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return SculkTomePolicy.mayTakeGunBenchResult(container, getItem());
    }

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true, remap = false)
    private void kingdoms$consumeSculkTome(Player player, ItemStack output, CallbackInfo callback) {
        if (!SculkTomePolicy.consumeAfterSuccessfulTake(container, output)) {
            callback.cancel();
        }
    }
}
