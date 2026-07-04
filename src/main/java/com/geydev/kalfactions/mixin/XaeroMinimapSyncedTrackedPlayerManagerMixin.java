package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.player.tracker.synced.ClientSyncedTrackedPlayerManager;

@Mixin(value = ClientSyncedTrackedPlayerManager.class, remap = false)
public abstract class XaeroMinimapSyncedTrackedPlayerManagerMixin {
    @Shadow
    public abstract void remove(UUID playerId);

    @Inject(
            method = "update(Ljava/util/UUID;DDDLnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$skipNonFactionPlayers(
            UUID playerId,
            double x,
            double y,
            double z,
            ResourceKey<Level> dimension,
            CallbackInfo ci
    ) {
        if (XaeroPlayerVisibility.shouldHide(playerId)) {
            remove(playerId);
            ci.cancel();
        }
    }
}
