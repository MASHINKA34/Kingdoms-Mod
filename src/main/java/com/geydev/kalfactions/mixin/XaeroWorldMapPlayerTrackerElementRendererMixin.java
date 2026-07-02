package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.map.radar.tracker.PlayerTrackerMapElement;
import xaero.map.radar.tracker.PlayerTrackerMapElementRenderer;

@Mixin(value = PlayerTrackerMapElementRenderer.class, remap = false)
public abstract class XaeroWorldMapPlayerTrackerElementRendererMixin {
    @Inject(
            method = "renderElement(Lxaero/map/radar/tracker/PlayerTrackerMapElement;ZDFDDLxaero/map/element/render/ElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lxaero/map/graphics/renderer/multitexture/MultiTextureRenderTypeRendererProvider;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionPlayers(
            PlayerTrackerMapElement<?> element,
            boolean hovering,
            double renderZ,
            float scale,
            double optionalScreenX,
            double optionalScreenY,
            ElementRenderInfo renderInfo,
            GuiGraphics graphics,
            MultiBufferSource.BufferSource bufferSource,
            MultiTextureRenderTypeRendererProvider rendererProvider,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (XaeroPlayerVisibility.shouldHide(element.getPlayerId())) {
            cir.setReturnValue(false);
        }
    }
}
