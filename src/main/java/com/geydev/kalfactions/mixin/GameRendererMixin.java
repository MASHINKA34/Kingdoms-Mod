package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.dungeon.DungeonSight;
import com.geydev.kalfactions.registry.ModEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(
            method = "getNightVisionScale(Lnet/minecraft/world/entity/LivingEntity;F)F",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void kingdoms$scaleDungeonSight(
            LivingEntity entity,
            float partialTick,
            CallbackInfoReturnable<Float> callback
    ) {
        if (entity.hasEffect(MobEffects.NIGHT_VISION)) {
            return;
        }
        MobEffectInstance sight = entity.getEffect(ModEffects.DUNGEON_SIGHT);
        if (sight != null) {
            callback.setReturnValue(DungeonSight.scaleFor(sight.getAmplifier()));
        }
    }
}
