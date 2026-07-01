package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementReader;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderContext;

@Mixin(value = PlayerTrackerMinimapElementReader.class, remap = false)
public abstract class XaeroMinimapPlayerTrackerElementReaderMixin {
    @Inject(
            method = "isHidden(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElementRenderContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionPlayers(
            PlayerTrackerMinimapElement<?> element,
            PlayerTrackerMinimapElementRenderContext context,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (XaeroPlayerVisibility.shouldHide(element.getPlayerId())) {
            cir.setReturnValue(true);
        }
    }
}
