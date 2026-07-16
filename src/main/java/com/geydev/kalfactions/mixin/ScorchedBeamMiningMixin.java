package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.MachineProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.common.BeamHandlerCommon;
import top.ribs.scguns.common.Gun;

@Mixin(value = BeamHandlerCommon.BeamMiningManager.class, remap = false)
public abstract class ScorchedBeamMiningMixin {
    @Inject(method = "updateBlockMining", at = @At("HEAD"), cancellable = true, remap = false)
    private static void kingdoms$guardBeamMining(
            Level level,
            BlockPos pos,
            ServerPlayer player,
            Gun gun,
            CallbackInfo callback
    ) {
        if (!MachineProtection.canPlayerMine(level, pos, player)) {
            callback.cancel();
        }
    }
}
