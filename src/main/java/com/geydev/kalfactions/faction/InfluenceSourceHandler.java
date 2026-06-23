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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InfluenceSourceHandler {
    private static final long DAY_MILLIS = 24L * 3_600_000L;
    private static final Map<String, Deque<Long>> KILL_AWARDS = new HashMap<>();
    private static final Map<String, Deque<Long>> CRAFT_AWARDS = new HashMap<>();
    private static final Map<UUID, Integer> MOB_PROGRESS = new HashMap<>();
    private static final Map<UUID, Deque<Long>> MOB_AWARDS = new HashMap<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == killer) {
            return;
        }
        FactionManager manager = FactionManager.get(killer.serverLevel());
        UUID killerFaction = manager.getFactionIdForMember(killer.getUUID()).orElse(null);
        if (killerFaction == null) {
            return;
        }
        if (victim instanceof ServerPlayer victimPlayer) {
            onPlayerKill(manager, killer, killerFaction, victimPlayer);
        } else if (victim instanceof Enemy) {
            onMobKill(manager, killer, killerFaction);
        }
    }

    private static void onPlayerKill(
            FactionManager manager,
            ServerPlayer killer,
            UUID killerFaction,
            ServerPlayer victim
    ) {
        UUID victimFaction = manager.getFactionIdForMember(victim.getUUID()).orElse(null);
        if (killerFaction.equals(victimFaction)) {
            return;
        }
        if (victimFaction != null) {
            com.geydev.kalfactions.war.WarManager wars =
                    com.geydev.kalfactions.war.WarManager.get(killer.serverLevel());
            if (wars.areAtWar(killerFaction, victimFaction)) {
                wars.recordWarKill(killer.getServer(), killerFaction);
            }
        }
        long amount = ModConfigSpec.INFLUENCE_KILL_INFLUENCE.getAsLong();
        if (amount <= 0L) {
            return;
        }
        long window = ModConfigSpec.INFLUENCE_KILL_CAP_HOURS.getAsInt() * 3_600_000L;
        int cap = ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.getAsInt();
        long now = System.currentTimeMillis();
        Deque<Long> awards = KILL_AWARDS.computeIfAbsent(
                killer.getUUID() + ":" + victim.getUUID(),
                ignored -> new ArrayDeque<>()
        );
        prune(awards, now, window);
        if (cap > 0 && awards.size() >= cap) {
            return;
        }
        FactionManager.OperationResult result = manager.grantInfluence(killerFaction, InfluenceType.MILITARY, amount);
        awards.addLast(now);
        if (result.successful()) {
            sendInfluenceToast(killer, InfluenceType.MILITARY, result.amount());
        }
    }

    private static void onMobKill(FactionManager manager, ServerPlayer killer, UUID killerFaction) {
        int perAward = ModConfigSpec.INFLUENCE_MOB_KILLS_PER_AWARD.getAsInt();
        long influence = ModConfigSpec.INFLUENCE_MOB_KILL_INFLUENCE.getAsLong();
        if (perAward <= 0 || influence <= 0L) {
            return;
        }
        UUID id = killer.getUUID();
        int progress = MOB_PROGRESS.merge(id, 1, Integer::sum);
        if (progress < perAward) {
            return;
        }
        MOB_PROGRESS.put(id, 0);
        long now = System.currentTimeMillis();
        Deque<Long> awards = MOB_AWARDS.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        prune(awards, now, DAY_MILLIS);
        long dailyCap = ModConfigSpec.INFLUENCE_MOB_DAILY_CAP.getAsLong();
        if (dailyCap > 0L && (long) awards.size() * influence >= dailyCap) {
            return;
        }
        FactionManager.OperationResult result = manager.grantInfluence(killerFaction, InfluenceType.MILITARY, influence);
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
        long perItem = ModConfigSpec.INFLUENCE_CRAFT_PER_ITEM.getAsLong();
        if (perItem <= 0L) {
            return;
        }
        int cap = ModConfigSpec.INFLUENCE_CRAFT_CAP_PER_ITEM.getAsInt();
        long window = ModConfigSpec.INFLUENCE_CRAFT_CAP_HOURS.getAsInt() * 3_600_000L;
        long now = System.currentTimeMillis();
        Deque<Long> awards = CRAFT_AWARDS.computeIfAbsent(
                player.getUUID() + ":" + itemId,
                ignored -> new ArrayDeque<>()
        );
        prune(awards, now, window);
        if (cap > 0 && awards.size() >= cap) {
            return;
        }
        FactionManager.OperationResult result = manager.grantInfluence(factionId, InfluenceType.SCIENCE, perItem);
        awards.addLast(now);
        if (result.successful()) {
            sendInfluenceToast(player, InfluenceType.SCIENCE, result.amount());
        }
    }

    private static void prune(Deque<Long> awards, long now, long window) {
        while (!awards.isEmpty() && now - awards.peekFirst() >= window) {
            awards.pollFirst();
        }
    }

    private static void sendInfluenceToast(ServerPlayer player, InfluenceType type, long amount) {
        if (amount > 0L) {
            PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CInfluenceGain(type.id(), amount));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        String idText = id.toString();
        KILL_AWARDS.keySet().removeIf(key -> key.startsWith(idText) || key.endsWith(idText));
        CRAFT_AWARDS.keySet().removeIf(key -> key.startsWith(idText));
        MOB_PROGRESS.remove(id);
        MOB_AWARDS.remove(id);
    }

    private InfluenceSourceHandler() {
    }
}
