package com.geydev.kalfactions.pvp;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.protection.FactionAccess;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
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

        if (FactionAccess.hasAnyBonus(attacker, FactionBonus.WARRIORS)) {
            float multiplier = ModConfigSpec.WARRIOR_DAMAGE_MULTIPLIER.get().floatValue();
            event.setAmount(event.getAmount() * multiplier);
        }

        int warriorLevels = com.geydev.kalfactions.faction.FactionManager.get(attacker.serverLevel())
                .getFactionForMember(attacker.getUUID())
                .map(faction -> faction.researchBonusCount("WARRIOR_DAMAGE"))
                .orElse(0);
        if (warriorLevels > 0) {
            event.setAmount(event.getAmount() * (1.0F + 0.05F * warriorLevels));
        }
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
