package com.geydev.kalfactions.mixin;

import net.mcreator.protectionpixel.init.ProtectionPixelModItems;
import net.mcreator.protectionpixel.network.ProtectionPixelModVariables;
import net.mcreator.protectionpixel.procedures.FlydamageProcedure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FlydamageProcedure.class)
public abstract class ProtectionPixelFlyDamageMixin {
    @Redirect(
            method = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/Commands;performPrefixedCommand"
                            + "(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;)V"
            )
    )
    private static void kingdoms$foldWingWithoutCuriosSlot(
            Commands commands,
            CommandSourceStack source,
            String command
    ) {
        Entity entity = source.getEntity();
        if (entity == null) {
            return;
        }
        ProtectionPixelModVariables.PlayerVariables variables =
                entity.getData(ProtectionPixelModVariables.PLAYER_VARIABLES);
        variables.motorinterface = new ItemStack(ProtectionPixelModItems.MANEUVERINGWING.get()).copy();
        variables.markSyncDirty();
    }
}
