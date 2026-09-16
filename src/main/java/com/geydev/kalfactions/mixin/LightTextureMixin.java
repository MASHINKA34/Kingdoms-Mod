package com.geydev.kalfactions.mixin;

import com.geydev.kalfactions.registry.ModEffects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
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
        return player.hasEffect(effect) || player.hasEffect(ModEffects.DUNGEON_SIGHT);
    }
}
