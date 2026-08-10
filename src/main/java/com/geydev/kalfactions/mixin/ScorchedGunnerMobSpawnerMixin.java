package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.scorched.DisabledMobGuns;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.config.GunnerMobSpawner;

@Mixin(value = GunnerMobSpawner.class, remap = false)
public abstract class ScorchedGunnerMobSpawnerMixin {
    @Inject(method = "tryEquipGunner", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kingdoms$skipGunnersInGunFreeZones(PathfinderMob mob, CallbackInfo callback) {
        if (DisabledMobGuns.blocksGunnerEquip(mob)) {
            callback.cancel();
        }
    }
}
