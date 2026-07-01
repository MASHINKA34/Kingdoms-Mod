package com.geydev.kalfactions.pvp;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.protection.FactionAccess;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class PvpHandler {
    private static final Map<UUID, Long> LAST_DENIAL_MESSAGE = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled()) {
            return;
        }
        applyArmorReduction(event);
        if (event.getEntity() instanceof ServerPlayer victim
                && ModConfigSpec.SANCTUARY_DISABLE_PVP.get()
                && isInSanctuary(victim)) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer victim
                && attacker != victim
                && ModConfigSpec.SANCTUARY_DISABLE_PVP.get()
                && isInSanctuary(attacker)) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer victim
                && attacker != victim
                && FactionAccess.sameFaction(attacker, victim)
                && !FactionAccess.internalPvpEnabled(attacker)
                && !DuelManager.isActive(attacker, victim)) {
            event.setCanceled(true);
            notifyFriendlyFire(attacker);
            return;
        }

        if (event.getEntity() instanceof LivingEntity victim
                && FactionAccess.hasAnyBonus(attacker, FactionBonus.ASSASSINS)
                && isBehind(attacker, victim)) {
            event.setAmount(event.getAmount() * ModConfigSpec.ASSASSIN_BACK_DAMAGE_MULTIPLIER.get().floatValue());
        }

        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }

        int warriorLevels = FactionManager.get(attacker.serverLevel())
                .getFactionForMember(attacker.getUUID())
                .map(faction -> faction.researchBonusCount("WARRIOR_DAMAGE"))
                .orElse(0);
        if (warriorLevels > 0) {
            event.setAmount(event.getAmount() * (1.0F + 0.05F * warriorLevels));
        }
    }

    private static boolean isBehind(ServerPlayer attacker, LivingEntity victim) {
        Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1.0D, 0.0D, 1.0D);
        if (toAttacker.lengthSqr() < 1.0E-4D) {
            return false;
        }
        Vec3 victimLook = victim.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (victimLook.lengthSqr() < 1.0E-4D) {
            return false;
        }
        return victimLook.normalize().dot(toAttacker.normalize()) < -0.5D;
    }

    private static void applyArmorReduction(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        int armorLevels = FactionManager.get(victim.serverLevel())
                .getFactionForMember(victim.getUUID())
                .map(faction -> faction.researchBonusCount("ARMOR_BOOST"))
                .orElse(0);
        if (armorLevels > 0) {
            float reduction = Math.min(0.50F, 0.05F * armorLevels);
            event.setAmount(event.getAmount() * (1.0F - reduction));
        }
    }

    private static boolean isInSanctuary(ServerPlayer player) {
        return SanctuaryManager.get(player.serverLevel()).isSanctuary(player.level(), player.blockPosition());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DENIAL_MESSAGE.remove(event.getEntity().getUUID());
    }

    private static void notifyFriendlyFire(ServerPlayer attacker) {
        long gameTime = attacker.serverLevel().getGameTime();
        long lastMessage = LAST_DENIAL_MESSAGE.getOrDefault(attacker.getUUID(), Long.MIN_VALUE);
        if (gameTime - lastMessage < 20L) {
            return;
        }
        LAST_DENIAL_MESSAGE.put(attacker.getUUID(), gameTime);
        attacker.displayClientMessage(
                Component.translatable("kingdoms.pvp.friendly_fire_disabled"),
                true
        );
    }

    private PvpHandler() {
    }
}
