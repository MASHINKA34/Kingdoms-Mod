package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.client.XaeroPlayerVisibility;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.hud.minimap.radar.state.RadarList;

@Mixin(value = RadarList.class, remap = false)
public abstract class XaeroRadarListMixin {
    @Shadow
    @Final
    private List<Entity> entities;

    @Inject(
            method = "add(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void kingdoms$skipNonFactionPlayers(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player && XaeroPlayerVisibility.shouldHide(player.getUUID())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "get(I)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), remap = false)
    private void kingdoms$pruneBeforeGet(int index, CallbackInfoReturnable<Entity> cir) {
        kingdoms$pruneHiddenPlayers();
    }

    @Inject(method = "size()I", at = @At("HEAD"), remap = false)
    private void kingdoms$pruneBeforeSize(CallbackInfoReturnable<Integer> cir) {
        kingdoms$pruneHiddenPlayers();
    }

    @Inject(method = "getEntities()Ljava/lang/Iterable;", at = @At("HEAD"), remap = false)
    private void kingdoms$pruneBeforeGetEntities(CallbackInfoReturnable<Iterable<Entity>> cir) {
        kingdoms$pruneHiddenPlayers();
    }

    private void kingdoms$pruneHiddenPlayers() {
        entities.removeIf(entity -> entity instanceof Player player && XaeroPlayerVisibility.shouldHide(player.getUUID()));
    }
}
