package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.charon.CharonService;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InfluenceSourceHandler {
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == killer) {
            return;
        }
        if (victim instanceof ServerPlayer ghost && CharonService.isGhostVictim(ghost)) {
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
        long granted = KillRewardLedger.get(killer.getServer()).awardPlayerKill(
                killer.getUUID(), victim.getUUID(), now, window, cap,
                () -> grantMilitary(manager, killerFaction, amount));
        sendInfluenceToast(killer, InfluenceType.MILITARY, granted);
    }

    private static void onMobKill(FactionManager manager, ServerPlayer killer, UUID killerFaction) {
        int perAward = ModConfigSpec.INFLUENCE_MOB_KILLS_PER_AWARD.getAsInt();
        long influence = ModConfigSpec.INFLUENCE_MOB_KILL_INFLUENCE.getAsLong();
        if (perAward <= 0 || influence <= 0L) {
            return;
        }
        long now = System.currentTimeMillis();
        long dailyCap = ModConfigSpec.INFLUENCE_MOB_DAILY_CAP.getAsLong();
        KillRewardLedger ledger = KillRewardLedger.get(killer.getServer());
        ledger.cleanup(now, ModConfigSpec.INFLUENCE_KILL_CAP_HOURS.getAsInt() * 3_600_000L);
        long granted = ledger.awardMobKill(killer.getUUID(), now, perAward, influence, dailyCap,
                amount -> grantMilitary(manager, killerFaction, amount));
        sendInfluenceToast(killer, InfluenceType.MILITARY, granted);
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
        ItemStack crafted = event.getCrafting();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(crafted.getItem());
        if (itemId == null) {
            return;
        }
        if (!countsAsDiscovery(itemId)) {
            return;
        }
        ScienceIncome.awardDiscovery(player, factionId, crafted);
    }

    public static boolean countsAsDiscovery(ResourceLocation itemId) {
        return itemId != null
                && (!itemId.getNamespace().equals("minecraft") || ModConfigSpec.SCIENCE_DISCOVERY_ALLOW_VANILLA.get());
    }

    private static long grantMilitary(FactionManager manager, UUID factionId, long amount) {
        FactionManager.OperationResult result = manager.grantInfluence(factionId, InfluenceType.MILITARY, amount);
        return result.successful() ? result.amount() : 0L;
    }

    private static void sendInfluenceToast(ServerPlayer player, InfluenceType type, long amount) {
        if (amount > 0L) {
            PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CInfluenceGain(type.id(), amount));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VillagerTradeRewards.clear(event.getEntity().getUUID());
    }

    private InfluenceSourceHandler() {
    }
}
