package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.FactionPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class InfluenceTicker {
    private static final int INTERVAL_TICKS = 100;
    private static final int FURNACE_INTERVAL = 12;
    private static int ticksSinceLastUpdate;
    private static int furnaceCounter;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticksSinceLastUpdate < INTERVAL_TICKS) {
            return;
        }
        ticksSinceLastUpdate = 0;
        MinecraftServer server = event.getServer();
        FactionManager manager = FactionManager.get(server);
        manager.tickInfluence(server.overworld().getGameTime());
        long intervalMillis = ModConfigSpec.INFLUENCE_DECAY_INTERVAL_HOURS.getAsInt() * 3_600_000L;
        manager.decayInfluence(
            System.currentTimeMillis(),
            intervalMillis,
            ModConfigSpec.INFLUENCE_DECAY_PERCENT.get()
        );
        ResearchManager.tick(manager);
        broadcastMiningBonus(server, manager);
        if (++furnaceCounter >= FURNACE_INTERVAL) {
            furnaceCounter = 0;
            awardFurnaceInfluence(server, manager);
            manager.applyTreasuryIncome();
        }
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

    private static void awardFurnaceInfluence(MinecraftServer server, FactionManager manager) {
        long perFurnace = ModConfigSpec.INFLUENCE_FURNACE_TICK.getAsLong();
        if (perFurnace <= 0L) {
            return;
        }
        for (Faction faction : manager.factions()) {
            long litFurnaces = 0L;
            for (ClaimKey claim : faction.claims()) {
                ServerLevel level = server.getLevel(claim.dimension());
                if (level == null) {
                    continue;
                }
                ChunkPos chunk = claim.chunk();
                if (!level.hasChunk(chunk.x, chunk.z)) {
                    continue;
                }
                LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
                if (loaded == null) {
                    continue;
                }
                for (BlockEntity blockEntity : loaded.getBlockEntities().values()) {
                    if (blockEntity instanceof AbstractFurnaceBlockEntity
                            && blockEntity.getBlockState().hasProperty(BlockStateProperties.LIT)
                            && blockEntity.getBlockState().getValue(BlockStateProperties.LIT)) {
                        litFurnaces++;
                    }
                }
            }
            if (litFurnaces > 0L) {
                manager.grantInfluence(faction.id(), InfluenceType.SCIENCE, litFurnaces * perFurnace);
            }
        }
    }

    private InfluenceTicker() {
    }
}
