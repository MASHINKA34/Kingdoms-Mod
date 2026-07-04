package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.radar.MinimapRadarList;

@Mixin(value = MinimapRadarList.class, remap = false)
public abstract class XaeroCommonMinimapRadarListMixin {
    @Inject(method = "getEntities()Ljava/util/List;", at = @At("RETURN"), remap = false)
    private void kingdoms$pruneHiddenPlayers(CallbackInfoReturnable<List<Entity>> cir) {
        cir.getReturnValue().removeIf(entity -> entity instanceof Player player
                && XaeroPlayerVisibility.shouldHide(player.getUUID()));
    }
}
