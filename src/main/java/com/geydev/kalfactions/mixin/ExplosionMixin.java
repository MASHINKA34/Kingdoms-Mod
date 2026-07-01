package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.ProtectionHandler;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Unique
    private static final ThreadLocal<Explosion> kingdoms$currentExplosion = new ThreadLocal<>();

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void kingdoms$trackExplosion(boolean spawnParticles, CallbackInfo ci) {
        kingdoms$currentExplosion.set((Explosion) (Object) this);
    }

    @Inject(method = "finalizeExplosion", at = @At("RETURN"))
    private void kingdoms$clearExplosion(boolean spawnParticles, CallbackInfo ci) {
        kingdoms$currentExplosion.remove();
    }

    @Inject(
            method = "addOrAppendStack",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void kingdoms$suppressWarExplosionDrops(
            List<Pair<ItemStack, BlockPos>> drops,
            ItemStack stack,
            BlockPos pos,
            CallbackInfo ci
    ) {
        Explosion explosion = kingdoms$currentExplosion.get();
        if (explosion != null
                && explosion.getDirectSourceEntity() != null
                && explosion.getDirectSourceEntity().level() instanceof ServerLevel level
                && ProtectionHandler.isWarExplosionDrop(level, pos, explosion)) {
            ci.cancel();
        }
    }
}
