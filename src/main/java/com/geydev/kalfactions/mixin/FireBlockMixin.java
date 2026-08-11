package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.sanctuary.SanctuaryFire;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(
            method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kingdoms$extinguishSanctuaryFire(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci
    ) {
        if (SanctuaryFire.blocksFire(level, pos)) {
            level.removeBlock(pos, false);
            ci.cancel();
        }
    }

    @Inject(
            method = "canSurvive(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kingdoms$denySanctuaryFirePlacement(
            BlockState state,
            LevelReader level,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (SanctuaryFire.blocksFire(level, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;I"
                    + "Lnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kingdoms$keepSanctuaryBlocksFromBurning(
            Level level,
            BlockPos pos,
            int chance,
            RandomSource random,
            int age,
            Direction face,
            CallbackInfo ci
    ) {
        if (SanctuaryFire.blocksFire(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void kingdoms$denySanctuaryIgnition(
            LevelReader level,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (SanctuaryFire.blocksFire(level, pos)) {
            cir.setReturnValue(0);
        }
    }
}
