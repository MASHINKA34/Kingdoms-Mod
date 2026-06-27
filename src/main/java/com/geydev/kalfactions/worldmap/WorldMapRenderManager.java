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
    private static final int SAMPLES_PER_TICK = 8_192;
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
        startMillis = System.currentTimeMillis();
        sinceBarUpdate = 0;
        bar = new ServerBossEvent(
                Component.literal(barName(level, 0.0, 0L)),
                BossEvent.BossBarColor.GREEN,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(0.0F);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            bar.addPlayer(player);
        }
        return true;
    }

    public static boolean cancel() {
        if (active == null) {
            return false;
        }
        active = null;
        clearBar();
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
            clearBar();
            return;
        }
        if (job.isDone()) {
            active = null;
            updateBar(job);
            clearBar();
            finish(job);
        } else if (++sinceBarUpdate >= BAR_UPDATE_INTERVAL_TICKS) {
            sinceBarUpdate = 0;
            updateBar(job);
        }
    }

    private static void updateBar(WorldMapRenderJob job) {
        ServerBossEvent activeBar = bar;
        if (activeBar == null) {
            return;
        }
        double progress = job.progress();
        for (ServerPlayer player : job.level().getServer().getPlayerList().getPlayers()) {
            activeBar.addPlayer(player);
        }
        activeBar.setProgress((float) Math.min(1.0, progress));
        long elapsedMillis = System.currentTimeMillis() - startMillis;
        long etaSeconds = progress > 0.0001
                ? (long) (elapsedMillis * (1.0 - progress) / progress / 1000.0)
                : 0L;
        activeBar.setName(Component.literal(barName(job.level(), progress, etaSeconds)));
    }

    private static void clearBar() {
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }
    }

    private static String barName(ServerLevel level, double progress, long etaSeconds) {
        return level.dimension().location()
                + " | " + String.format(Locale.ROOT, "%.2f", progress * 100.0) + "%"
                + " | " + formatEta(etaSeconds);
    }

    private static String formatEta(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", safe / 3600, (safe % 3600) / 60, safe % 60);
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
                    job.resolution(),
                    job.level().dimension().location()
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
