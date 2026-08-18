package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScienceIncome {
    public static long grantDailyCapped(
            MinecraftServer server,
            UUID factionId,
            long amount,
            ServerPlayer notify
    ) {
        if (server == null) {
            return 0L;
        }
        return grantDailyCapped(
                FactionManager.get(server),
                ScienceLedger.get(server),
                factionId,
                amount,
                notify
        );
    }

    public static long grantDailyCapped(
            FactionManager factions,
            ScienceLedger ledger,
            UUID factionId,
            long amount,
            ServerPlayer notify
    ) {
        if (factionId == null || amount <= 0L) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        long dailyCap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
        long granted = Math.min(amount, ledger.remainingToday(factionId, now, dailyCap));
        if (granted > 0L && !factions.grantInfluence(factionId, InfluenceType.SCIENCE, granted).successful()) {
            return 0L;
        }
        if (granted > 0L) {
            ledger.recordScience(factionId, now, granted);
        }
        if (granted < amount) {
            notifyCapReached(ledger, factionId, now, dailyCap, notify);
        }
        return granted;
    }

    public static long awardDiscovery(ServerPlayer player, UUID factionId, ItemStack stack) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0L;
        }
        return awardDiscovery(
                FactionManager.get(server),
                ScienceLedger.get(server),
                factionId,
                stack,
                player
        );
    }

    public static long awardDiscovery(
            FactionManager factions,
            ScienceLedger ledger,
            UUID factionId,
            ItemStack stack,
            ServerPlayer notify
    ) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || ledger.isDiscovered(factionId, itemId)) {
            return 0L;
        }
        long reward = discoveryReward(stack);
        if (reward <= 0L) {
            return 0L;
        }
        long granted = grantDailyCapped(factions, ledger, factionId, reward, notify);
        if (granted <= 0L) {
            return 0L;
        }
        ledger.discover(factionId, itemId);
        if (notify != null) {
            PacketDistributor.sendToPlayer(
                    notify,
                    new FactionPayloads.S2CScienceDiscovery(itemId.toString(), granted)
            );
        }
        return granted;
    }

    public static long discoveryReward(ItemStack stack) {
        return discoveryReward(
                ModConfigSpec.SCIENCE_DISCOVERY_INFLUENCE.getAsLong(),
                ScienceTags.discoveryMultiplier(stack)
        );
    }

    public static long discoveryReward(long base, double multiplier) {
        if (base <= 0L || multiplier <= 0.0D) {
            return 0L;
        }
        return Math.max(0L, Math.round(base * multiplier));
    }

    private static void notifyCapReached(
            ScienceLedger ledger,
            UUID factionId,
            long now,
            long dailyCap,
            ServerPlayer notify
    ) {
        if (notify == null || !ledger.markCapNotified(factionId, now, notify.getUUID())) {
            return;
        }
        notify.sendSystemMessage(Component.translatable("kingdoms.science.daily_cap_reached", dailyCap));
    }

    private ScienceIncome() {
    }
}
