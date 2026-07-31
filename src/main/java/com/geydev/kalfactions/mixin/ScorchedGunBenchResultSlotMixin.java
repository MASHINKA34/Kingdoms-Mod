package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.scorched.GunBenchBlueprintConsumption;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "top.ribs.scguns.client.screen.GunBenchMenu$2", remap = false)
public abstract class ScorchedGunBenchResultSlotMixin extends Slot {
    @Unique
    private boolean kingdoms$consumeBlueprintAfterTake;

    private ScorchedGunBenchResultSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        return GunBenchBlueprintConsumption.mayTakeResult(player.level(), container);
    }

    @Inject(method = "onTake", at = @At("HEAD"), remap = false)
    private void kingdoms$prepareBlueprintConsumption(Player player, ItemStack output, CallbackInfo callback) {
        kingdoms$consumeBlueprintAfterTake = !player.level().isClientSide
                && GunBenchBlueprintConsumption.hasMatchingRecipe(player.level(), container);
    }

    @Inject(method = "onTake", at = @At("TAIL"), remap = false)
    private void kingdoms$consumeBlueprintAfterSuccessfulTake(Player player, ItemStack output, CallbackInfo callback) {
        if (kingdoms$consumeBlueprintAfterTake) {
            GunBenchBlueprintConsumption.consumeOne(container);
            kingdoms$consumeBlueprintAfterTake = false;
        }
    }
}
