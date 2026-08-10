package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public final class ClusterMaintenance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROGRESS_INTERVAL_TICKS = 40;

    private static Job job;

    public static synchronized boolean isRunning() {
        return job != null;
    }

    public static synchronized int processed() {
        return job == null ? 0 : job.processed;
    }

    public static synchronized int total() {
        return job == null ? 0 : job.keys.size();
    }

    public static synchronized boolean start(List<Long> chunkKeys, UUID initiator) {
        if (job != null) {
            return false;
        }
        job = new Job(List.copyOf(chunkKeys), initiator);
        return true;
    }

    public static void tick(ServerLevel level) {
        Job current;
        synchronized (ClusterMaintenance.class) {
            current = job;
        }
        if (current == null) {
            return;
        }
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        int budget = ModConfigSpec.CLUSTER_MAINTENANCE_CHUNKS_PER_TICK.getAsInt();
        while (budget-- > 0 && current.processed < current.keys.size()) {
            manager.resetChunk(level, current.keys.get(current.processed));
            current.processed++;
        }
        if (current.processed >= current.keys.size()) {
            synchronized (ClusterMaintenance.class) {
                job = null;
            }
            DrillService.notifyAllViewers(level);
            report(level, current.initiator, Component.translatable(
                    "commands.kingdoms.cluster.done",
                    current.processed
            ));
            return;
        }
        if (++current.sinceReport >= PROGRESS_INTERVAL_TICKS) {
            current.sinceReport = 0;
            report(level, current.initiator, Component.translatable(
                    "commands.kingdoms.cluster.progress",
                    current.processed,
                    current.keys.size()
            ));
        }
    }

    private static void report(ServerLevel level, UUID initiator, Component message) {
        ServerPlayer player = initiator == null ? null : level.getServer().getPlayerList().getPlayer(initiator);
        if (player != null) {
            player.sendSystemMessage(message);
            return;
        }
        LOGGER.info(message.getString());
    }

    private static final class Job {
        private final List<Long> keys;
        private final UUID initiator;
        private int processed;
        private int sinceReport;

        private Job(List<Long> keys, UUID initiator) {
            this.keys = keys;
            this.initiator = initiator;
        }
    }

    private ClusterMaintenance() {
    }
}
