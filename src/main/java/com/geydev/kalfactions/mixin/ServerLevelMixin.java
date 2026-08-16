package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.dimension.DimensionControlManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void kingdoms$useDimensionSeed(CallbackInfoReturnable<Long> callback) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (DimensionControlManager.isControlled(level.dimension())) {
            callback.setReturnValue(
                    kingdoms$dimensionSeed(level.getServer().getWorldData().worldGenOptions().seed())
            );
        }
    }

    @WrapOperation(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J")
    )
    private long kingdoms$wrapDimensionSeed(WorldOptions options, Operation<Long> original) {
        return kingdoms$dimensionSeed(original.call(options));
    }

    private long kingdoms$dimensionSeed(long seed) {
        ServerLevel level = (ServerLevel) (Object) this;
        return DimensionControlManager.get(level.getServer()).generationSeed(level.dimension(), seed);
    }
}
