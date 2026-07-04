package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer;
import xaero.hud.minimap.player.tracker.PlayerTrackerIconRenderer;

@Mixin(value = PlayerTrackerIconRenderer.class, remap = false)
public abstract class XaeroMinimapPlayerTrackerIconRendererMixin {
    @Inject(
            method = "renderIcon(Lnet/minecraft/client/Minecraft;Lxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;F)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionTrackedPlayerIcon(
            Minecraft minecraft,
            MultiTextureRenderTypeRenderer renderer,
            PoseStack poseStack,
            Player player,
            ResourceLocation skin,
            float alpha,
            CallbackInfo ci
    ) {
        if (player != null && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            ci.cancel();
        }
    }
}
