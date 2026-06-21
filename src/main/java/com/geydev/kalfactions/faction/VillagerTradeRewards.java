package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VillagerTradeRewards {
    private static final long TOAST_THROTTLE_MILLIS = 4000L;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void onVillagerTrade(ServerPlayer player, ItemStack bought) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            return;
        }
        long perTrade = ModConfigSpec.INFLUENCE_VILLAGER_TRADE.getAsLong();
        if (perTrade > 0L) {
            FactionManager.OperationResult result =
                    manager.grantInfluence(factionId, InfluenceType.ECONOMIC, perTrade);
            if (result.successful()) {
                throttledToast(player, result.amount());
            }
        }
        int extraLevels = manager.getFactionById(factionId)
                .map(faction -> faction.researchBonusCount("VILLAGER_EXTRA"))
                .orElse(0);
        if (extraLevels > 0 && !bought.isEmpty()) {
            double chance = Math.min(0.60D, 0.25D * extraLevels);
            if (player.serverLevel().getRandom().nextDouble() < chance) {
                ItemStack bonus = bought.copy();
                if (!player.getInventory().add(bonus)) {
                    player.drop(bonus, false);
                }
            }
        }
    }

    private static void throttledToast(ServerPlayer player, long amount) {
        long now = System.currentTimeMillis();
        Pending pending = PENDING.computeIfAbsent(player.getUUID(), ignored -> new Pending());
        pending.amount += amount;
        if (now - pending.lastFlush < TOAST_THROTTLE_MILLIS) {
            return;
        }
        long flush = pending.amount;
        pending.amount = 0L;
        pending.lastFlush = now;
        PacketDistributor.sendToPlayer(
                player,
                new FactionPayloads.S2CInfluenceGain(InfluenceType.ECONOMIC.id(), flush)
        );
    }

    public static void clear(UUID playerId) {
        PENDING.remove(playerId);
    }

    private static final class Pending {
        private long amount;
        private long lastFlush;
    }

    private VillagerTradeRewards() {
    }
}
