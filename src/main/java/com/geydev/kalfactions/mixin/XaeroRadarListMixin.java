package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.radar.state.RadarList;

@Mixin(value = RadarList.class, remap = false)
public abstract class XaeroRadarListMixin {
    @Inject(
            method = "add(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$skipNonFactionPlayers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
