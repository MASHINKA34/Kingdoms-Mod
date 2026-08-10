package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.entity.MapScoutEntity;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveStore;
import com.geydev.kalfactions.net.FactionServerHooks;
import com.geydev.kalfactions.tax.OfflineNoticeQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScoutService {
    public static final double MAX_NPC_DISTANCE_SQUARED = 100.0D;

    private static final long ACTION_COOLDOWN_TICKS = 4L;
    private static final int STATE_SYNC_INTERVAL_TICKS = 40;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, ScoutPayloads.S2CScoutState> LAST_STATE = new ConcurrentHashMap<>();
    private static final Map<UUID, ScoutJob> JOBS = new HashMap<>();
    private static int ticksUntilStateSync = STATE_SYNC_INTERVAL_TICKS;
    private static final ExecutorService IO_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public static void openOrderScreen(ServerPlayer player, MapScoutEntity scout) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        FactionManager factions = FactionManager.get(player.serverLevel());
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        ScoutManager manager = ScoutManager.get(server);
        ScoutOrder active = manager.activeOrder(faction.id()).orElse(null);
        if (active != null) {
            FactionServerHooks.sendNotice(player, busyNotice(active), false);
            pushState(player);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        boolean officer = member != null && member.role().isAtLeast(FactionRole.OFFICER);
        List<ScoutPayloads.PackageEntry> packages = new ArrayList<>();
        for (ScoutPackage option : ScoutPackage.values()) {
            packages.add(new ScoutPayloads.PackageEntry(
                    option.ordinal(),
                    option.sizeChunks(),
                    option.durationMinutes(),
                    option.price()
            ));
        }
        PacketDistributor.sendToPlayer(player, new ScoutPayloads.S2COpenScout(
                packages,
                faction.treasuryBalance(),
                officer,
                scout.blockPosition()
        ));
    }

    public static void placeOrder(ServerPlayer player, ScoutPayloads.C2SScoutOrder payload) {
        MinecraftServer server = player.getServer();
        if (server == null || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }
        if (payload.packageOrdinal() < 0 || payload.packageOrdinal() >= ScoutPackage.values().length) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.package"), false);
            return;
        }
        ScoutPackage option = ScoutPackage.byOrdinal(payload.packageOrdinal());
        ServerLevel level = player.serverLevel();
        ResourceLocation dimensionId = level.dimension().location();
        if (!dimensionId.equals(payload.dimension()) || !isAllowedDimension(dimensionId)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.dimension"), false);
            return;
        }
        if (nearbyScout(player) == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.too_far"), false);
            return;
        }
        FactionManager factions = FactionManager.get(level);
        Faction faction = factions.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().isAtLeast(FactionRole.OFFICER)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.not_officer"), false);
            return;
        }
        int size = option.sizeChunks();
        if (size * size > ModConfigSpec.SCOUT_MAX_CHUNKS_PER_ORDER.getAsInt()) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.too_large"), false);
            return;
        }
        if (!withinBorder(level, payload.centerChunkX(), payload.centerChunkZ(), size)) {
            FactionServerHooks.sendNotice(player, Component.translatable("kingdoms.scout.error.border"), false);
            return;
        }
        ScoutManager manager = ScoutManager.get(server);
        long price = option.price();
        ScoutOrder order = new ScoutOrder(
                UUID.randomUUID(),
                option,
                level.dimension(),
                payload.centerChunkX(),
                payload.centerChunkZ(),
                size,
                System.currentTimeMillis(),
                option.durationMillis(),
                price,
                player.getUUID()
        );
        if (!manager.startOrder(faction.id(), order)) {
            ScoutOrder active = manager.activeOrder(faction.id()).orElse(null);
            FactionServerHooks.sendNotice(
                    player,
                    active == null
                            ? Component.translatable("kingdoms.scout.error.busy")
                            : busyNotice(active),
                    false
            );
            return;
        }
        if (!factions.withdraw(faction.id(), price).successful()) {
            manager.removeOrder(faction.id());
            FactionServerHooks.sendNotice(
                    player,
                    Component.translatable("kingdoms.scout.error.funds", NumismaticsEconomy.format(price)),
                    false
            );
            return;
        }
        Component notice = Component.translatable(
                "kingdoms.scout.ordered",
                size,
                size,
                option.durationMinutes(),
                NumismaticsEconomy.format(price)
        );
        broadcastToFaction(server, faction, notice, true);
        pushStateToFaction(server, faction.id());
    }

    public static void tick(MinecraftServer server) {
        if (--ticksUntilStateSync <= 0) {
            ticksUntilStateSync = STATE_SYNC_INTERVAL_TICKS;
            syncChangedStates(server);
        }
        ScoutManager manager = ScoutManager.get(server);
        List<Map.Entry<UUID, ScoutOrder>> active = manager.activeOrders();
        if (active.isEmpty()) {
            JOBS.clear();
            return;
        }
        JOBS.keySet().removeIf(factionId -> manager.activeOrder(factionId).isEmpty());
        int budget = ModConfigSpec.SCOUT_CHUNKS_PER_TICK.getAsInt();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ScoutOrder> entry : active) {
            UUID factionId = entry.getKey();
            ScoutOrder order = entry.getValue();
            if (!order.scanned() && budget > 0) {
                budget -= advance(server, manager, factionId, order, budget);
            }
            if (order.scanned() && !order.delivered() && order.timeElapsed(now)) {
                deliver(server, manager, factionId, order);
            }
        }
    }

    private static int advance(
            MinecraftServer server,
            ScoutManager manager,
            UUID factionId,
            ScoutOrder order,
            int budget
    ) {
        ServerLevel level = server.getLevel(order.dimension());
        if (level == null) {
            return 0;
        }
        ScoutJob job = JOBS.get(factionId);
        if (job == null || job.order() != order) {
            job = new ScoutJob(order, level);
            JOBS.put(factionId, job);
        }
        int processed = job.tick(budget);
        if (!job.isDone()) {
            return processed;
        }
        JOBS.remove(factionId);
        try {
            List<String> regions = job.writeStaging(stagingRoot(server, order.id()));
            order.markScanned(regions);
            manager.markDirty();
        } catch (IOException | RuntimeException exception) {
            KalFactions.LOGGER.warn("Scout order {} failed to stage its Xaero regions", order.id(), exception);
            refundAndDrop(server, manager, factionId, order, "kingdoms.scout.failed");
        }
        return processed;
    }

    private static void deliver(MinecraftServer server, ScoutManager manager, UUID factionId, ScoutOrder order) {
        if (order.deliveryInFlight()) {
            return;
        }
        Path staging = stagingRoot(server, order.id());
        List<String> names = order.stagedRegions();
        List<XaeroArchiveStore.IncomingRegion> incoming = new ArrayList<>(names.size());
        for (String name : names) {
            incoming.add(new XaeroArchiveStore.IncomingRegion(name, staging.resolve(name)));
        }
        XaeroArchiveStore.ArchiveLocation location;
        try {
            location = XaeroArchiveStore.location(server, factionId, order.dimension().location());
        } catch (IOException | RuntimeException exception) {
            KalFactions.LOGGER.warn("Scout order {} has no valid faction archive path", order.id(), exception);
            refundAndDrop(server, manager, factionId, order, "kingdoms.scout.failed");
            return;
        }
        order.setDeliveryInFlight(true);
        IO_EXECUTOR.execute(() -> {
            boolean merged = false;
            try {
                if (!incoming.isEmpty()) {
                    XaeroArchiveStore.merge(location, incoming);
                }
                merged = true;
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.warn("Scout order {} could not be merged into the faction archive", order.id(), exception);
            }
            boolean successful = merged;
            server.execute(() -> finishDelivery(server, manager, factionId, order, successful));
        });
    }

    private static void finishDelivery(
            MinecraftServer server,
            ScoutManager manager,
            UUID factionId,
            ScoutOrder order,
            boolean successful
    ) {
        order.setDeliveryInFlight(false);
        if (!successful) {
            refundAndDrop(server, manager, factionId, order, "kingdoms.scout.failed");
            return;
        }
        order.markDelivered();
        manager.markDirty();
        clearStaging(server, order.id());
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        Component notice = Component.translatable("kingdoms.scout.returned");
        if (faction != null) {
            broadcastToFaction(server, faction, notice, true);
        }
        pushStateToFaction(server, factionId);
    }

    private static void refundAndDrop(
            MinecraftServer server,
            ScoutManager manager,
            UUID factionId,
            ScoutOrder order,
            String messageKey
    ) {
        JOBS.remove(factionId);
        clearStaging(server, order.id());
        manager.removeOrder(factionId);
        FactionManager factions = FactionManager.get(server);
        if (order.paid() > 0L) {
            factions.deposit(factionId, order.paid());
        }
        Faction faction = factions.getFactionById(factionId).orElse(null);
        if (faction != null) {
            broadcastToFaction(
                    server,
                    faction,
                    Component.translatable(messageKey, NumismaticsEconomy.format(order.paid())),
                    false
            );
        }
        pushStateToFaction(server, factionId);
    }

    public static boolean cancel(MinecraftServer server, UUID factionId) {
        ScoutManager manager = ScoutManager.get(server);
        ScoutOrder order = manager.activeOrder(factionId).orElse(null);
        if (order == null) {
            return false;
        }
        refundAndDrop(server, manager, factionId, order, "kingdoms.scout.cancelled");
        return true;
    }

    public static boolean completeNow(MinecraftServer server, UUID factionId) {
        ScoutManager manager = ScoutManager.get(server);
        ScoutOrder order = manager.activeOrder(factionId).orElse(null);
        if (order == null) {
            return false;
        }
        if (!order.scanned()) {
            ServerLevel level = server.getLevel(order.dimension());
            if (level == null) {
                return false;
            }
            ScoutJob job = JOBS.get(factionId);
            if (job == null || job.order() != order) {
                job = new ScoutJob(order, level);
            }
            job.tick(order.chunkCount());
            JOBS.remove(factionId);
            try {
                order.markScanned(job.writeStaging(stagingRoot(server, order.id())));
                manager.markDirty();
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.warn("Scout order {} failed to stage its Xaero regions", order.id(), exception);
                refundAndDrop(server, manager, factionId, order, "kingdoms.scout.failed");
                return false;
            }
        }
        deliver(server, manager, factionId, order);
        return true;
    }

    public static void pushState(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        UUID factionId = FactionManager.get(server).getFactionIdForMember(player.getUUID()).orElse(null);
        ScoutOrder order = factionId == null
                ? null
                : ScoutManager.get(server).activeOrder(factionId).orElse(null);
        ScoutPayloads.S2CScoutState state = order == null
                ? ScoutPayloads.S2CScoutState.idle()
                : new ScoutPayloads.S2CScoutState(
                        true,
                        order.endsAtMillis(),
                        order.progressPercent(),
                        order.sizeChunks(),
                        order.centerChunkX(),
                        order.centerChunkZ()
                );
        LAST_STATE.put(player.getUUID(), state);
        PacketDistributor.sendToPlayer(player, state);
    }

    private static void syncChangedStates(MinecraftServer server) {
        FactionManager factions = FactionManager.get(server);
        ScoutManager manager = ScoutManager.get(server);
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            UUID factionId = factions.getFactionIdForMember(player.getUUID()).orElse(null);
            boolean busy = factionId != null && manager.hasActiveOrder(factionId);
            ScoutPayloads.S2CScoutState previous = LAST_STATE.get(player.getUUID());
            if (previous != null && previous.active() == busy) {
                continue;
            }
            pushState(player);
        }
    }

    public static void pushStateToFaction(MinecraftServer server, UUID factionId) {
        Faction faction = FactionManager.get(server).getFactionById(factionId).orElse(null);
        if (faction == null) {
            return;
        }
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online != null) {
                pushState(online);
            }
        }
    }

    public static Component busyNotice(ScoutOrder order) {
        return Component.translatable(
                "kingdoms.scout.busy",
                formatRemaining(order.remainingMillis(System.currentTimeMillis()))
        );
    }

    public static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static MapScoutEntity nearbyScout(ServerPlayer player) {
        List<MapScoutEntity> candidates = player.serverLevel().getEntitiesOfClass(
                MapScoutEntity.class,
                player.getBoundingBox().inflate(Math.sqrt(MAX_NPC_DISTANCE_SQUARED)),
                scout -> scout.isAlive() && scout.distanceToSqr(player) <= MAX_NPC_DISTANCE_SQUARED
        );
        return candidates.stream()
                .min(Comparator.comparingDouble(scout -> scout.distanceToSqr(player)))
                .orElse(null);
    }

    public static boolean isAllowedDimension(ResourceLocation dimensionId) {
        for (String allowed : ModConfigSpec.SCOUT_ALLOWED_DIMENSIONS.get()) {
            if (dimensionId.toString().equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    public static ResourceKey<Level> firstAllowedDimension() {
        for (String allowed : ModConfigSpec.SCOUT_ALLOWED_DIMENSIONS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(allowed);
            if (id != null) {
                return ResourceKey.create(Registries.DIMENSION, id);
            }
        }
        return Level.OVERWORLD;
    }

    public static void onServerStarted(MinecraftServer server) {
        JOBS.clear();
        ScoutManager manager = ScoutManager.get(server);
        List<UUID> known = manager.activeOrders().stream().map(entry -> entry.getValue().id()).toList();
        Path root = stagingBase(server);
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var paths = Files.list(root)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                UUID id;
                try {
                    id = UUID.fromString(path.getFileName().toString());
                } catch (IllegalArgumentException exception) {
                    return;
                }
                if (!known.contains(id)) {
                    deleteRecursively(path);
                }
            });
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Could not prune scout staging directories", exception);
        }
    }

    public static void onLogout(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
        LAST_STATE.remove(playerId);
    }

    private static void broadcastToFaction(
            MinecraftServer server,
            Faction faction,
            Component notice,
            boolean successful
    ) {
        OfflineNoticeQueue offlineQueue = OfflineNoticeQueue.get(server);
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer online = server.getPlayerList().getPlayer(memberId);
            if (online == null) {
                offlineQueue.enqueue(server, memberId, notice, successful);
            } else {
                FactionServerHooks.sendNotice(online, notice, successful);
            }
        }
    }

    private static boolean withinBorder(ServerLevel level, int centerChunkX, int centerChunkZ, int size) {
        int half = (size - 1) / 2;
        int minChunkX = centerChunkX - half;
        int minChunkZ = centerChunkZ - half;
        int maxChunkX = minChunkX + size - 1;
        int maxChunkZ = minChunkZ + size - 1;
        return level.getWorldBorder().isWithinBounds(new ChunkPos(minChunkX, minChunkZ))
                && level.getWorldBorder().isWithinBounds(new ChunkPos(maxChunkX, maxChunkZ));
    }

    private static Path stagingBase(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .resolve("kingdoms")
                .resolve("scout_staging");
    }

    private static Path stagingRoot(MinecraftServer server, UUID orderId) {
        Path base = stagingBase(server);
        Path root = base.resolve(orderId.toString()).normalize();
        if (!root.startsWith(base)) {
            throw new IllegalStateException("Invalid scout staging path");
        }
        return root;
    }

    private static void clearStaging(MinecraftServer server, UUID orderId) {
        deleteRecursively(stagingRoot(server, orderId));
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    KalFactions.LOGGER.warn("Could not delete scout staging file {}", path, exception);
                }
            });
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Could not delete scout staging directory {}", root, exception);
        }
    }

    public static boolean spawn(ServerLevel level, double x, double y, double z, float yRot) {
        MapScoutEntity scout = com.geydev.kalfactions.registry.ModEntities.MAP_SCOUT.get().create(level);
        if (scout == null) {
            return false;
        }
        scout.moveTo(x, y, z, yRot, 0.0F);
        scout.setYBodyRot(yRot);
        scout.setYHeadRot(yRot);
        scout.yBodyRotO = yRot;
        scout.yHeadRotO = yRot;
        if (!level.addFreshEntity(scout)) {
            return false;
        }
        ScoutManager.get(level.getServer()).addPost(scout.getUUID(), level.dimension(), scout.blockPosition());
        return true;
    }

    private ScoutService() {
    }
}
