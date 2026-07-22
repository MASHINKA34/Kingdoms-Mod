package com.geydev.kalfactions.sculk;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class NaturalSculkEvents {
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.isNewChunk()
                && event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            NaturalSculkCleaner.enqueue(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            NaturalSculkCleaner.finishBeforeUnload(level, chunk);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        NaturalSculkCleaner.tick(event.getServer().getAllLevels());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        NaturalSculkCleaner.clear();
    }

    private NaturalSculkEvents() {
    }
}
