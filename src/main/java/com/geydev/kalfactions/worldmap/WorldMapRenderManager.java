package com.geydev.kalfactions.worldmap;

import com.geydev.kalfactions.KalFactions;
import java.io.IOException;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class WorldMapRenderManager {
    private static final int SAMPLES_PER_TICK = 24_576;
    private static final int BAR_UPDATE_INTERVAL_TICKS = 10;

    @Nullable
    private static WorldMapRenderJob active;
    @Nullable
    private static ServerBossEvent bar;
    private static long startMillis;
    private static int sinceBarUpdate;

    private WorldMapRenderManager() {
    }

    public static boolean isRunning() {
        return active != null;
    }

    public static int progressPercent() {
        return active == null ? -1 : active.progressPercent();
    }

    public static boolean start(ServerLevel level, int centerX, int centerZ, int regionBlocks, int resolution) {
        if (active != null) {
            return false;
        }
        active = new WorldMapRenderJob(level, centerX, centerZ, regionBlocks, resolution);
        return true;
    }

    public static boolean cancel() {
        if (active == null) {
            return false;
        }
        active = null;
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        WorldMapRenderJob job = active;
        if (job == null) {
            return;
        }
        try {
            job.tick(SAMPLES_PER_TICK);
        } catch (RuntimeException e) {
            KalFactions.LOGGER.error("World map render failed", e);
            active = null;
            return;
        }
        if (job.isDone()) {
            active = null;
            finish(job);
        }
    }

    private static void finish(WorldMapRenderJob job) {
        MinecraftServer server = job.level().getServer();
        try {
            WorldMapStorage.save(
                    server,
                    job.image(),
                    job.centerX(),
                    job.centerZ(),
                    job.regionBlocks(),
                    job.resolution()
            );
            WorldMapService.broadcast(server);
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Kingdoms] World map render complete."),
                    false
            );
        } catch (IOException e) {
            KalFactions.LOGGER.error("Saving world map failed", e);
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[Kingdoms] World map render failed to save (see log)."),
                    false
            );
        }
    }
}
