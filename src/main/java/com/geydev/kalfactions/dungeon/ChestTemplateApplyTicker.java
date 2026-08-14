package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ChestTemplateApplyTicker {
    private static final int CHUNKS_PER_TICK = 4;
    private static final Deque<ApplyJob> JOBS = new ArrayDeque<>();

    public static boolean enqueue(
            ServerPlayer player,
            DungeonManager.DungeonView dungeon,
            ChestTemplate template,
            boolean applyCooldown
    ) {
        synchronized (JOBS) {
            if (JOBS.stream().anyMatch(job -> job.playerId.equals(player.getUUID()))) {
                return false;
            }
            List<Long> chunks = new ArrayList<>(dungeon.chunks().size());
            for (ClaimKey key : dungeon.chunks()) {
                if (key.dimension().equals(player.serverLevel().dimension())) {
                    chunks.add(ChunkPos.asLong(key.x(), key.z()));
                }
            }
            JOBS.add(new ApplyJob(
                    player.getUUID(),
                    player.serverLevel().dimension(),
                    dungeon.name(),
                    template,
                    applyCooldown,
                    chunks.iterator()
            ));
        }
        return true;
    }

    public static void clear() {
        synchronized (JOBS) {
            JOBS.clear();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ApplyJob job;
        synchronized (JOBS) {
            job = JOBS.peek();
        }
        if (job == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(job.dimension);
        if (level == null) {
            finish(server, job);
            return;
        }
        for (int index = 0; index < CHUNKS_PER_TICK && job.chunks.hasNext(); index++) {
            job.updated += applyToChunk(level, job.chunks.next(), job.template, job.applyCooldown);
        }
        if (!job.chunks.hasNext()) {
            finish(server, job);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
        ChestTemplateService.reset();
    }

    private static int applyToChunk(ServerLevel level, long packedChunk, ChestTemplate template, boolean cooldown) {
        ChunkPos chunk = new ChunkPos(packedChunk);
        int updated = 0;
        for (BlockPos pos : List.copyOf(level.getChunk(chunk.x, chunk.z).getBlockEntitiesPos())) {
            if (level.getBlockEntity(pos) instanceof DungeonChestBlockEntity chest) {
                template.applyTo(chest, cooldown);
                DungeonManager.get(level).trackChest(level, pos);
                updated++;
            }
        }
        return updated;
    }

    private static void finish(MinecraftServer server, ApplyJob job) {
        synchronized (JOBS) {
            JOBS.remove(job);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(job.playerId);
        if (player != null) {
            ChestTemplateService.notice(
                    player,
                    Component.translatable(
                            "kingdoms.dungeon.template_apply_all_done",
                            job.updated,
                            job.dungeonName
                    ),
                    true
            );
        }
    }

    private static final class ApplyJob {
        private final UUID playerId;
        private final ResourceKey<Level> dimension;
        private final String dungeonName;
        private final ChestTemplate template;
        private final boolean applyCooldown;
        private final Iterator<Long> chunks;
        private int updated;

        private ApplyJob(
                UUID playerId,
                ResourceKey<Level> dimension,
                String dungeonName,
                ChestTemplate template,
                boolean applyCooldown,
                Iterator<Long> chunks
        ) {
            this.playerId = playerId;
            this.dimension = dimension;
            this.dungeonName = dungeonName;
            this.template = template;
            this.applyCooldown = applyCooldown;
            this.chunks = chunks;
        }
    }

    private ChestTemplateApplyTicker() {
    }
}
