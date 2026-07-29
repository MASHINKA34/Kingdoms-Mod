package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class QuarryEvents {
    private static final Set<PendingGeneration> pendingGenerations = new LinkedHashSet<>();
    private static int ticksUntilUpdate;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos chunk = new ChunkPos(event.getChunk().getPos().toLong());
            if (QuarryManager.isNaturalCandidate(level, chunk)) {
                pendingGenerations.add(new PendingGeneration(level.dimension(), chunk.toLong()));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        drainPending(event.getServer());
        if (--ticksUntilUpdate > 0) {
            return;
        }
        ticksUntilUpdate = 20;
        QuarryManager.get(event.getServer()).tick(event.getServer());
        QuarryService.tickOpenMenus(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QuarryManager.get(event.getServer()).clearBossBars();
        pendingGenerations.clear();
        ticksUntilUpdate = 0;
    }

    static void cancelPending(ServerLevel level, ChunkPos chunk) {
        pendingGenerations.remove(new PendingGeneration(level.dimension(), chunk.toLong()));
    }

    private static void drainPending(MinecraftServer server) {
        for (PendingGeneration pending : Set.copyOf(pendingGenerations)) {
            pendingGenerations.remove(pending);
            ServerLevel level = server.getLevel(pending.dimension());
            ChunkPos chunk = new ChunkPos(pending.chunk());
            if (level != null && level.hasChunk(chunk.x, chunk.z)) {
                QuarryManager.get(level).generateIfCandidate(level, chunk);
            }
        }
    }

    private record PendingGeneration(ResourceKey<Level> dimension, long chunk) {
    }

    private QuarryEvents() {
    }
}
