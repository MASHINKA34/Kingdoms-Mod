package com.geydev.kalfactions.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.entity.SculkCatalystBlockEntity$CatalystListener")
public abstract class SculkCatalystListenerMixin {
    @Shadow
    @Final
    private SculkSpreader sculkSpreader;

    @Inject(method = "handleGameEvent", at = @At("HEAD"), cancellable = true)
    private void kingdoms$disableNaturalSpread(
            ServerLevel level,
            Holder<GameEvent> event,
            GameEvent.Context context,
            Vec3 position,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(false);
    }

    @Inject(method = "getSculkSpreader", at = @At("HEAD"))
    private void kingdoms$discardPendingSpread(CallbackInfoReturnable<SculkSpreader> callback) {
        sculkSpreader.clear();
    }
}
