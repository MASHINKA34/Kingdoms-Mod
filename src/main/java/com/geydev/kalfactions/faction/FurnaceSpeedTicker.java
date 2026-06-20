package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class FurnaceSpeedTicker {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        FactionManager manager = FactionManager.get(server);
        for (Faction faction : manager.factions()) {
            int levels = faction.researchBonusCount("SMELT_SPEED");
            if (levels <= 0) {
                continue;
            }
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
                    boost(blockEntity);
                }
            }
        }
    }

    private static void boost(BlockEntity blockEntity) {
        if (!(blockEntity instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }
        if (!furnace.getBlockState().hasProperty(BlockStateProperties.LIT)
                || !furnace.getBlockState().getValue(BlockStateProperties.LIT)) {
            return;
        }
        if (furnace.cookingProgress <= 0 || furnace.cookingProgress >= furnace.cookingTotalTime - 1) {
            return;
        }
        furnace.cookingProgress = furnace.cookingProgress + 1;
    }

    private FurnaceSpeedTicker() {
    }
}
