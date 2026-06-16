package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InfluenceSourceHandler {
    private static final Map<String, Deque<Long>> KILL_AWARDS = new HashMap<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim) {
            return;
        }
        FactionManager manager = FactionManager.get(killer.serverLevel());
        UUID killerFaction = manager.getFactionIdForMember(killer.getUUID()).orElse(null);
        UUID victimFaction = manager.getFactionIdForMember(victim.getUUID()).orElse(null);
        if (killerFaction == null || victimFaction == null || killerFaction.equals(victimFaction)) {
            return;
        }
        com.geydev.kalfactions.war.WarManager wars =
                com.geydev.kalfactions.war.WarManager.get(killer.serverLevel());
        if (wars.areAtWar(killerFaction, victimFaction)) {
            long warPoints = ModConfigSpec.WAR_KILL_POINTS.getAsInt();
            int killPointLevels = manager.getFactionById(killerFaction)
                    .map(f -> f.researchBonusCount("WAR_KILL_POINTS"))
                    .orElse(0);
            if (killPointLevels > 0) {
                warPoints = warPoints + Math.round(warPoints * 0.25D * killPointLevels);
            }
            wars.addWarPoints(killer.getServer(), killerFaction, warPoints);
        }
        long amount = ModConfigSpec.INFLUENCE_KILL_INFLUENCE.getAsLong();
        if (amount <= 0L) {
            return;
        }
        long window = ModConfigSpec.INFLUENCE_KILL_CAP_HOURS.getAsInt() * 3_600_000L;
        int cap = ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.getAsInt();
        long now = System.currentTimeMillis();
        String key = killer.getUUID() + ":" + victim.getUUID();
        Deque<Long> awards = KILL_AWARDS.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        while (!awards.isEmpty() && now - awards.peekFirst() >= window) {
            awards.pollFirst();
        }
        if (cap > 0 && awards.size() >= cap) {
            return;
        }
        FactionManager.OperationResult result = manager.grantInfluence(killerFaction, InfluenceType.MILITARY, amount);
        awards.addLast(now);
        if (result.successful()) {
            sendInfluenceToast(killer, InfluenceType.MILITARY, result.amount());
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getCrafting().isEmpty()) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        if (itemId == null || itemId.getNamespace().equals("minecraft")) {
            return;
        }
        FactionManager.OperationResult result = manager.grantInfluence(factionId, InfluenceType.SCIENCE, 1L);
        if (result.successful()) {
            sendInfluenceToast(player, InfluenceType.SCIENCE, result.amount());
        }
    }

    private static void sendInfluenceToast(ServerPlayer player, InfluenceType type, long amount) {
        if (amount > 0L) {
            PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CInfluenceGain(type.id(), amount));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        String id = event.getEntity().getUUID().toString();
        KILL_AWARDS.keySet().removeIf(key -> key.startsWith(id) || key.endsWith(id));
    }

    private InfluenceSourceHandler() {
    }
}
