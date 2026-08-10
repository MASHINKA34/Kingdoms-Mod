package com.geydev.kalfactions.mixin;

import net.mcreator.protectionpixel.entity.ArmorentityEntity;
import net.mcreator.protectionpixel.procedures.ArmorrenderProcedure;
import net.minecraft.client.Options;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ArmorrenderProcedure.class)
public abstract class ProtectionPixelArmorRenderMixin {
    @Unique
    private static final int KINGDOMS$MAX_HANGER_SCAN_CHUNKS = 4;

    @Unique
    private static ArmorentityEntity kingdoms$reusedArmorStand;

    @Redirect(
            method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getEffectiveRenderDistance()I")
    )
    private static int kingdoms$limitHangerScanRange(Options options) {
        return Math.min(options.getEffectiveRenderDistance(), KINGDOMS$MAX_HANGER_SCAN_CHUNKS);
    }

    @Redirect(
            method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;)V",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)"
                            + "Lnet/mcreator/protectionpixel/entity/ArmorentityEntity;"
            )
    )
    private static ArmorentityEntity kingdoms$reuseArmorStand(EntityType<ArmorentityEntity> type, Level level) {
        ArmorentityEntity cached = kingdoms$reusedArmorStand;
        if (cached == null || cached.level() != level) {
            cached = new ArmorentityEntity(type, level);
            kingdoms$reusedArmorStand = cached;
        }
        return cached;
    }
}
