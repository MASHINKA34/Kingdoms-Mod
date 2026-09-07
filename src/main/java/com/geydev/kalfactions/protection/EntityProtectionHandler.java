package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class EntityProtectionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isProtectedFrom(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            deny(event.getEntity(), event.getHand());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isProtectedFrom(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            deny(event.getEntity(), event.getHand());
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isProtectedFrom(event.getEntity(), event.getTarget())) {
            event.setCanceled(true);
            deny(event.getEntity(), InteractionHand.MAIN_HAND);
        }
    }

    @SubscribeEvent
    public static void onInvulnerabilityCheck(EntityInvulnerabilityCheckEvent event) {
        if (!event.isInvulnerable()
                && event.getSource().getEntity() instanceof Player attacker
                && isProtectedFrom(attacker, event.getEntity())) {
            event.setInvulnerable(true);
        }
    }

    public static boolean isProtectedFrom(Player player, Entity target) {
        if (!isProtectedEntity(target)
                || !(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof ServerLevel level)
                || target.level() != level) {
            return false;
        }
        return !ProtectionHandler.canModifyAt(serverPlayer, level, target.blockPosition());
    }

    private static boolean isProtectedEntity(Entity entity) {
        return entity instanceof HangingEntity
                || entity instanceof ArmorStand
                || entity instanceof AbstractMinecart
                || entity instanceof Boat
                || entity instanceof AbstractVillager
                || entity instanceof Animal;
    }

    private static void deny(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                    Component.translatable("kingdoms.protection.no_entity_interact"),
                    true
            );
        }
    }

    private EntityProtectionHandler() {
    }
}
