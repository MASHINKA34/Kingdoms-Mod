package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

@Mixin(value = ProjectileEntity.class, remap = false)
public abstract class ScorchedProjectileMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), remap = false)
    private void kingdoms$beginProjectileTick(CallbackInfo callback) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!self.level().isClientSide()) {
            MachineProtection.beginProjectileContext(self.getShooter());
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"), remap = false)
    private void kingdoms$endProjectileTick(CallbackInfo callback) {
        MachineProtection.endProjectileContext();
    }
}
