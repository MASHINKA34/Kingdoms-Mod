package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ResourceClusterEvents {
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)
                && event.getChunk() instanceof LevelChunk chunk) {
            ResourceClusterManager.get(level).queue(chunk.getPos(), level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)
                && event.getChunk() instanceof LevelChunk chunk) {
            ResourceClusterManager.get(level).deactivate(chunk.getPos());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel level = event.getServer().overworld();
        ResourceClusterManager.get(level).tick(level);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOreBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(Level.OVERWORLD)) {
            ResourceClusterManager.OreConsumption result =
                    ResourceClusterManager.get(level).consumeTrackedOre(level, event.getPos());
            if (result == ResourceClusterManager.OreConsumption.DEPLETED_NO_DROP) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        for (net.minecraft.core.BlockPos pos : List.copyOf(event.getAffectedBlocks())) {
            if (manager.consumeTrackedOre(level, pos) == ResourceClusterManager.OreConsumption.DEPLETED_NO_DROP) {
                event.getAffectedBlocks().remove(pos);
            }
        }
    }

    private ResourceClusterEvents() {
    }
}
