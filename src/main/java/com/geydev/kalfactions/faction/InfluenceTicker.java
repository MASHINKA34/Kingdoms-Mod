package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InfluenceTicker {
    private static final int INTERVAL_TICKS = 100;
    private static int ticksSinceLastUpdate;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticksSinceLastUpdate < INTERVAL_TICKS) {
            return;
        }
        ticksSinceLastUpdate = 0;
        MinecraftServer server = event.getServer();
        FactionManager manager = FactionManager.get(server);
        long intervalMillis = ModConfigSpec.INFLUENCE_DECAY_INTERVAL_HOURS.getAsInt() * 3_600_000L;
        manager.decayInfluence(
            System.currentTimeMillis(),
            intervalMillis,
            ModConfigSpec.INFLUENCE_DECAY_PERCENT.get()
        );
        ResearchManager.tick(manager);
        broadcastMiningBonus(server, manager);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendMiningBonus(player, FactionManager.get(player.serverLevel()));
        }
    }

    private static void broadcastMiningBonus(MinecraftServer server, FactionManager manager) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendMiningBonus(player, manager);
        }
    }

    private static void sendMiningBonus(ServerPlayer player, FactionManager manager) {
        float multiplier = manager.getFactionForMember(player.getUUID())
            .map(Faction::miningSpeedMultiplier)
            .orElse(1.0D)
            .floatValue();
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CMiningBonus(multiplier));
    }

    private InfluenceTicker() {
    }
}
