package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.entity.projectile.LightningProjectileEntity;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

@Mixin(value = LightningProjectileEntity.class, remap = false)
public abstract class ScorchedLightningProjectileMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), remap = false)
    private void kingdoms$beginLightningTick(CallbackInfo callback) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!self.level().isClientSide()) {
            MachineProtection.beginProjectileContext(self.getShooter(), self.level().getGameTime());
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"), remap = false)
    private void kingdoms$endLightningTick(CallbackInfo callback) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!self.level().isClientSide()) {
            MachineProtection.endProjectileContext();
        }
    }
}
