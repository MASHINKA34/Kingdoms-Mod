package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.dungeon.DungeonSight;
import com.geydev.kalfactions.registry.ModEffects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Redirect(
            method = "updateLightTexture(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z",
                    ordinal = 0
            )
    )
    private boolean kingdoms$treatDungeonSightAsNightVision(LocalPlayer player, Holder<MobEffect> effect) {
        if (player.hasEffect(effect)) {
            return true;
        }
        MobEffectInstance sight = player.getEffect(ModEffects.DUNGEON_SIGHT);
        if (sight == null) {
            return false;
        }
        float waterVision = player.getWaterVision();
        float vanilla = waterVision > 0.0F && player.hasEffect(MobEffects.CONDUIT_POWER) ? waterVision : 0.0F;
        return DungeonSight.scaleFor(sight.getAmplifier()) > vanilla;
    }
}
