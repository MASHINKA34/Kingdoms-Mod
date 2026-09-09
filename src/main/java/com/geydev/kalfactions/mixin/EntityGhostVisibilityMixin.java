package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.charon.CharonGhosts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGhostVisibilityMixin {
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void kingdoms$revealGhosts(Player viewer, CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof Player self && CharonGhosts.isGhost(self.getUUID())) {
            callback.setReturnValue(false);
        }
    }
}
