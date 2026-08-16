package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.bonus.NomadRiding;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
    @Inject(method = "getControllingPassenger", at = @At("HEAD"), cancellable = true)
    private void kingdoms$rideWithoutSaddle(CallbackInfoReturnable<LivingEntity> callback) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (self.isSaddled()) {
            return;
        }
        Entity passenger = self.getFirstPassenger();
        if (passenger instanceof Player player && NomadRiding.canRideUnsaddled(player)) {
            callback.setReturnValue(player);
        }
    }

    @Inject(method = "canJump", at = @At("HEAD"), cancellable = true)
    private void kingdoms$jumpWithoutSaddle(CallbackInfoReturnable<Boolean> callback) {
        if (kingdoms$unsaddledNomadRider()) {
            callback.setReturnValue(true);
        }
    }

    @WrapOperation(
            method = "onPlayerJump",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/horse/AbstractHorse;isSaddled()Z"
            )
    )
    private boolean kingdoms$saddledOrNomad(AbstractHorse horse, Operation<Boolean> original) {
        return original.call(horse) || kingdoms$unsaddledNomadRider();
    }

    private boolean kingdoms$unsaddledNomadRider() {
        AbstractHorse self = (AbstractHorse) (Object) this;
        return self.getFirstPassenger() instanceof Player player && NomadRiding.canRideUnsaddled(player);
    }
}
