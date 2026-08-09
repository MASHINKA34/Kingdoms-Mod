package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ResourceClusterEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClusterBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        BlockPos pos = event.getPos();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        if (manager.consumeMinedBlock(level, pos) > 0) {
            DrillService.notifyClusterChanged(level, new ChunkPos(pos));
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)) {
            ResourceClusterManager.get(level).queue(event.getChunk().getPos(), level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)) {
            ResourceClusterManager.get(level).deactivate(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        ResourceClusterManager.get(level).tick(level);
    }

    private ResourceClusterEvents() {
    }
}
