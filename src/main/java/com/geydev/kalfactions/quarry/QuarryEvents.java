package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class QuarryEvents {
    private static int ticksUntilUpdate;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            QuarryManager.get(level).generateIfCandidate(level, new ChunkPos(event.getChunk().getPos().toLong()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (--ticksUntilUpdate > 0) {
            return;
        }
        ticksUntilUpdate = 20;
        QuarryManager.get(event.getServer()).tick(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QuarryManager.get(event.getServer()).clearBossBars();
        ticksUntilUpdate = 0;
    }

    private QuarryEvents() {
    }
}
