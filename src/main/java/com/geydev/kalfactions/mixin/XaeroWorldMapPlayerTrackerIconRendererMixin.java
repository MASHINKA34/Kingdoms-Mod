package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.radar.tracker.PlayerTrackerIconRenderer;

@Mixin(value = PlayerTrackerIconRenderer.class, remap = false)
public abstract class XaeroWorldMapPlayerTrackerIconRendererMixin {
    @Inject(
            method = "renderIcon(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$hideNonFactionTrackedPlayerIcon(
            GuiGraphics graphics,
            Player player,
            ResourceLocation skin,
            CallbackInfo ci
    ) {
        if (player != null && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            ci.cancel();
        }
    }
}
