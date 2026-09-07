package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.protection.ProtectionHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

    /**
     * Wrapped rather than injected so a throwing drop handler cannot leave the tracked explosion
     * behind and make every later drop consult a stale one.
     */
    @WrapMethod(method = "finalizeExplosion")
    private void kingdoms$trackExplosion(boolean spawnParticles, Operation<Void> original) {
        kingdoms$currentExplosion.set((Explosion) (Object) this);
        try {
            original.call(spawnParticles);
        } finally {
            kingdoms$currentExplosion.remove();
        }
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
