package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer;

@Mixin(value = PlayerTrackerMinimapElementRenderer.class, remap = false)
public abstract class XaeroMinimapPlayerTrackerElementRendererMixin {
    @Inject(
            method = "renderElement(Lxaero/hud/minimap/player/tracker/PlayerTrackerMinimapElement;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionPlayers(
            PlayerTrackerMinimapElement<?> element,
            boolean hovering,
            boolean optional,
            double renderZ,
            float scale,
            double optionalScreenX,
            double optionalScreenY,
            MinimapElementRenderInfo renderInfo,
            GuiGraphics graphics,
            MultiBufferSource.BufferSource bufferSource,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (XaeroPlayerVisibility.shouldHide(element.getPlayerId())) {
            cir.setReturnValue(false);
        }
    }
}
