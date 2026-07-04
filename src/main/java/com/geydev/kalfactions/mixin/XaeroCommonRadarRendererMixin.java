package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;

@Mixin(value = xaero.common.minimap.render.radar.element.RadarRenderer.class, remap = false)
public abstract class XaeroCommonRadarRendererMixin {
    @Inject(
            method = "renderElement(Lnet/minecraft/world/entity/Entity;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionRadarPlayers(
            Entity entity,
            boolean hovering,
            boolean highlighted,
            double renderZ,
            float scale,
            double optionalScreenX,
            double optionalScreenY,
            MinimapElementRenderInfo renderInfo,
            GuiGraphics graphics,
            MultiBufferSource.BufferSource bufferSource,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof Player player && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
