package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.entity.projectile.ShotballProjectileEntity;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

@Mixin(value = ShotballProjectileEntity.class, remap = false)
public abstract class ScorchedShotballProjectileMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), remap = false)
    private void kingdoms$beginShotballTick(CallbackInfo callback) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!self.level().isClientSide()) {
            MachineProtection.beginProjectileContext(self.getShooter(), self.level().getGameTime());
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"), remap = false)
    private void kingdoms$endShotballTick(CallbackInfo callback) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!self.level().isClientSide()) {
            MachineProtection.endProjectileContext();
        }
    }
}
