package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.radar.render.element.RadarRenderContext;

@Mixin(value = xaero.common.minimap.render.radar.element.RadarElementReader.class, remap = false)
public abstract class XaeroRadarElementReaderMixin {
    @Inject(
            method = "isHidden(Lnet/minecraft/world/entity/Entity;Lxaero/hud/minimap/radar/render/element/RadarRenderContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionPlayers(
            Entity entity,
            RadarRenderContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof Player player && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
