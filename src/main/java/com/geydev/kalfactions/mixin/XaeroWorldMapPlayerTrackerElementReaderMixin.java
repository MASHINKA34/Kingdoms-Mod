package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.radar.tracker.PlayerTrackerMapElementReader;
import xaero.map.radar.tracker.PlayerTrackerMapElementRenderContext;

@Mixin(value = PlayerTrackerMapElementReader.class, remap = false)
public abstract class XaeroWorldMapPlayerTrackerElementReaderMixin {
    @Inject(
            method = "isHidden(Lxaero/map/radar/tracker/PlayerTrackerMapElement;Lxaero/map/radar/tracker/PlayerTrackerMapElementRenderContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionPlayers(
            PlayerTrackerMapElement<?> element,
            PlayerTrackerMapElementRenderContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (XaeroPlayerVisibility.shouldHide(element.getPlayerId())) {
            cir.setReturnValue(true);
        }
    }
}
