package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElement;
import xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementCollector;

@Mixin(value = PlayerTrackerMinimapElementCollector.class, remap = false)
public abstract class XaeroMinimapPlayerTrackerElementCollectorMixin {
    @Shadow
    private Map<UUID, PlayerTrackerMinimapElement<?>> elements;

    @Inject(method = "update(Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"), remap = false)
    private void kingdoms$removeNonFactionPlayers(Minecraft minecraft, CallbackInfo ci) {
        if (elements != null) {
            elements.keySet().removeIf(XaeroPlayerVisibility::shouldHide);
        }
    }
}
