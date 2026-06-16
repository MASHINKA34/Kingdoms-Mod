package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
            if (manager.getFactionById(killerFaction)
                    .map(f -> f.hasResearchBonus(ResearchBonus.WAR_KILL_POINTS))
                    .orElse(false)) {
                warPoints = warPoints + Math.round(warPoints * 0.25D);
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
        manager.grantInfluence(killerFaction, InfluenceType.MILITARY, amount);
        awards.addLast(now);
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
        ChunkPos chunk = new ChunkPos(player.blockPosition());
        UUID territoryFaction = manager.getFactionAt(player.serverLevel(), chunk)
                .map(Faction::id)
                .orElse(null);
        if (!factionId.equals(territoryFaction)) {
            return;
        }
        double chance = ModConfigSpec.INFLUENCE_CRAFT_CHANCE.get();
        if (chance <= 0.0D || player.serverLevel().getRandom().nextDouble() >= chance) {
            return;
        }
        manager.grantInfluence(factionId, InfluenceType.SCIENCE, 1L);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        String id = event.getEntity().getUUID().toString();
        KILL_AWARDS.keySet().removeIf(key -> key.startsWith(id) || key.endsWith(id));
    }

    private InfluenceSourceHandler() {
    }
}
