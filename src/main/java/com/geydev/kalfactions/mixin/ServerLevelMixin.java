package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.dimension.DimensionControlManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void kingdoms$useDimensionSeed(CallbackInfoReturnable<Long> callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (DimensionControlManager.isControlled(level.dimension())) {
            callback.setReturnValue(kingdoms$dimensionSeed(level.getServer().getWorldData().worldGenOptions()));
        }
    }

    @Redirect(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J")
    )
    private long kingdoms$dimensionSeed(WorldOptions options) {
        ServerLevel level = (ServerLevel) (Object) this;
        return DimensionControlManager.get(level.getServer()).generationSeed(level.dimension(), options.seed());
    }
}
