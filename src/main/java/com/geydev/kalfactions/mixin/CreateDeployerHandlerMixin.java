package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.content.kinetics.deployer.DeployerHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeployerHandler.class)
public abstract class CreateDeployerHandlerMixin {
    @Inject(method = "activate", at = @At("HEAD"), cancellable = true)
    private static void kingdoms$protectQuarryFromDeployer(
            DeployerFakePlayer player,
            Vec3 vec,
            BlockPos pos,
            Vec3 extension,
            @Coerce Object mode,
            CallbackInfo ci
    ) {
        if (!MachineProtection.canContraptionAct(player.level(), pos)) {
            ci.cancel();
        }
    }
}
