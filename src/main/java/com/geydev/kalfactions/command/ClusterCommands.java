package com.geydev.kalfactions.command;

import com.geydev.kalfactions.outpost.cluster.ClusterMaintenance;
import com.geydev.kalfactions.outpost.cluster.DrillService;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class ClusterCommands {
    private static final int MAX_RESET_RADIUS = 64;
    private static final int STATUS_SEARCH_RADIUS = 4;
    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final Map<String, Confirmation> PENDING = new ConcurrentHashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cluster")
                .then(Commands.literal("status").executes(ClusterCommands::status))
                .then(Commands.literal("reset")
                        .then(Commands.literal("here")
                                .executes(context -> resetRadius(context, 0)))
                        .then(Commands.literal("radius")
                                .then(Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_RESET_RADIUS))
                                        .executes(context -> resetRadius(
                                                context,
                                                IntegerArgumentType.getInteger(context, "chunks")
                                        ))))
                        .then(Commands.literal("all").executes(ClusterCommands::resetAll)))
                .then(Commands.literal("regenerate")
                        .then(Commands.literal("all").executes(ClusterCommands::regenerateAll)))
                .then(Commands.literal("drain").executes(ClusterCommands::drain));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        ChunkPos origin = new ChunkPos(BlockPos.containing(source.getPosition()));
        ChunkPos found = nearestCluster(manager, origin);
        if (found == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.cluster.status.none", origin.x, origin.z));
            return 0;
        }
        ChunkPos chunk = found;
        ResourceClusterManager.ClusterView cluster = manager.clusterAt(chunk).orElseThrow();
        ResourceClusterManager.ReserveView reserve = manager.reserveAt(chunk).orElseThrow();
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.cluster.status.header",
                cluster.type().displayName(),
                chunk.x,
                chunk.z,
                cluster.richness()
        ), false);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.cluster.status.reserve",
                reserve.remaining(),
                reserve.limit(),
                cluster.basePos().toShortString()
        ), false);
        if (reserve.timerRunning()) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.cluster.status.timer",
                    days(reserve.restoreInMillis()),
                    clock(reserve.restoreInMillis())
            ), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.cluster.status.no_timer"), false);
        }
        if (reserve.exhausted()) {
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.cluster.status.exhausted"), false);
        }
        manager.boundDrillAt(chunk).ifPresent(drill -> source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.cluster.status.drill",
                drill.toShortString()
        ), false));
        return 1;
    }

    private static int drain(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        ChunkPos origin = new ChunkPos(BlockPos.containing(source.getPosition()));
        ChunkPos found = nearestCluster(manager, origin);
        if (found == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.cluster.status.none", origin.x, origin.z));
            return 0;
        }
        int drained = manager.drain(level, found);
        DrillService.notifyClusterChanged(level, found);
        ResourceClusterManager.ReserveView reserve = manager.reserveAt(found).orElseThrow();
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.cluster.drain.done",
                found.x,
                found.z,
                drained,
                days(reserve.restoreInMillis()),
                clock(reserve.restoreInMillis())
        ), true);
        return 1;
    }

    private static ChunkPos nearestCluster(ResourceClusterManager manager, ChunkPos origin) {
        if (manager.clusterAt(origin).isPresent()) {
            return origin;
        }
        for (int radius = 1; radius <= STATUS_SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    ChunkPos candidate = new ChunkPos(origin.x + dx, origin.z + dz);
                    if (manager.clusterAt(candidate).isPresent()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static int resetRadius(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        if (busy(source)) {
            return 0;
        }
        ChunkPos origin = new ChunkPos(BlockPos.containing(source.getPosition()));
        List<Long> keys = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                keys.add(ChunkPos.asLong(origin.x + dx, origin.z + dz));
            }
        }
        return begin(source, keys);
    }

    private static int resetAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (busy(source)) {
            return 0;
        }
        ResourceClusterManager manager = ResourceClusterManager.get(source.getLevel());
        ResourceClusterManager.MaintenancePreview preview = manager.preview(null);
        if (!confirmed(source, "reset_all")) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.cluster.reset.confirm",
                    preview.clusters(),
                    preview.runningTimers(),
                    preview.exhausted(),
                    preview.tombstones()
            ), false);
            return 0;
        }
        Set<Long> keys = manager.knownChunkKeys();
        keys.addAll(loadedChunkKeys(source.getLevel()));
        return begin(source, new ArrayList<>(keys));
    }

    private static int regenerateAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (busy(source)) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        ResourceClusterManager manager = ResourceClusterManager.get(level);
        ResourceClusterManager.MaintenancePreview preview = manager.preview(null);
        if (!confirmed(source, "regenerate_all")) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.cluster.regenerate.confirm",
                    preview.clusters(),
                    preview.drillBindings(),
                    manager.effectiveGeneration() + 1
            ), false);
            return 0;
        }
        int moved = manager.beginRegeneration();
        Set<Long> keys = manager.knownChunkKeys();
        keys.addAll(loadedChunkKeys(level));
        int generation = manager.effectiveGeneration();
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.cluster.regenerate.started",
                generation,
                moved
        ), true);
        return begin(source, new ArrayList<>(keys));
    }

    private static int begin(CommandSourceStack source, List<Long> keys) {
        UUID initiator = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        if (!ClusterMaintenance.start(keys, initiator)) {
            source.sendFailure(Component.translatable(
                    "commands.kingdoms.cluster.busy",
                    ClusterMaintenance.processed(),
                    ClusterMaintenance.total()
            ));
            return 0;
        }
        int total = keys.size();
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.cluster.started", total), true);
        return 1;
    }

    private static boolean busy(CommandSourceStack source) {
        if (!ClusterMaintenance.isRunning()) {
            return false;
        }
        source.sendFailure(Component.translatable(
                "commands.kingdoms.cluster.busy",
                ClusterMaintenance.processed(),
                ClusterMaintenance.total()
        ));
        return true;
    }

    private static Set<Long> loadedChunkKeys(ServerLevel level) {
        Set<Long> keys = new HashSet<>();
        int view = level.getServer().getPlayerList().getViewDistance() + 1;
        for (ServerPlayer player : level.players()) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    if (level.hasChunk(chunkX, chunkZ)) {
                        keys.add(ChunkPos.asLong(chunkX, chunkZ));
                    }
                }
            }
        }
        for (long forced : level.getForcedChunks()) {
            keys.add(forced);
        }
        return keys;
    }

    private static boolean confirmed(CommandSourceStack source, String kind) {
        String key = source.getEntity() instanceof ServerPlayer player
                ? player.getStringUUID()
                : "console";
        long now = System.currentTimeMillis();
        Confirmation pending = PENDING.get(key);
        if (pending != null && pending.kind().equals(kind) && now < pending.expiresAt()) {
            PENDING.remove(key);
            return true;
        }
        PENDING.put(key, new Confirmation(kind, now + CONFIRM_WINDOW_MILLIS));
        return false;
    }

    private static long days(long millis) {
        return Math.max(0L, millis) / 1000L / 86400L;
    }

    private static String clock(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return String.format("%02d:%02d", seconds % 86400L / 3600L, seconds % 3600L / 60L);
    }

    private record Confirmation(String kind, long expiresAt) {
    }

    private ClusterCommands() {
    }
}
