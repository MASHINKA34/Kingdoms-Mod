package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.radar.render.element.RadarRenderer;

@Mixin(value = RadarRenderer.class, remap = false)
public abstract class XaeroRadarRendererMixin {
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

    @Inject(
            method = "renderSingleEntity(Lnet/minecraft/world/entity/Entity;ZZFZZLxaero/hud/minimap/element/render/MinimapElementRenderLocation;Lcom/mojang/blaze3d/pipeline/RenderTarget;Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionSingleRadarPlayers(
            Entity entity,
            boolean cave,
            boolean listed,
            float scale,
            boolean iconsAllowed,
            boolean labelsAllowed,
            MinimapElementRenderLocation location,
            RenderTarget framebuffer,
            GuiGraphics graphics,
            CallbackInfo ci
    ) {
        if (entity instanceof Player player && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            ci.cancel();
        }
    }
}
