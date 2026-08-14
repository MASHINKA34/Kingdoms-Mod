package com.geydev.kalfactions.bonus;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class CropGrowthTicker {
    private static final int INTERVAL_TICKS = 20;
    private static final int PICKS_PER_SECTION = 3;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL_TICKS != 0) {
            return;
        }
        FactionManager manager = FactionManager.get(server);
        for (Faction faction : manager.factions()) {
            if (!faction.hasLegacyMastery(FactionBonus.FARMERS)) {
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
                if (loaded != null) {
                    growChunk(level, loaded, chunk);
                }
            }
        }
    }

    private static void growChunk(ServerLevel level, LevelChunk chunk, ChunkPos pos) {
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        LevelChunkSection[] sections = chunk.getSections();
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.isRandomlyTicking()) {
                continue;
            }
            int bottomY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int i = 0; i < PICKS_PER_SECTION; i++) {
                BlockPos target = level.getBlockRandomPos(minX, bottomY, minZ, 15);
                BlockState state = level.getBlockState(target);
                if (state.is(BlockTags.CROPS) && state.isRandomlyTicking()) {
                    state.randomTick(level, target, level.random);
                }
            }
        }
    }

    private CropGrowthTicker() {
    }
}
