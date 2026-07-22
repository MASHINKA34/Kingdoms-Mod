package com.geydev.kalfactions.integration.xaero.archive;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.storage.LevelResource;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class XaeroArchiveSessions {
    private static final Map<UUID, UploadSession> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, OutboundSession> DOWNLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> DOWNLOAD_RESERVATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_STATS_REQUEST = new ConcurrentHashMap<>();
    private static final Object CAPACITY_LOCK = new Object();
    private static final Object LIFECYCLE_COMMIT_LOCK = new Object();
    private static final ExecutorService IO_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final AtomicLong SERVER_GENERATION = new AtomicLong();
    private static volatile MinecraftServer activeServer;

    public static void beginUpload(ServerPlayer player, XaeroArchivePayloads.C2SBeginUpload payload) {
        ServerLifecycle lifecycle = lifecycle(player);
        if (lifecycle == null) {
            fail(player, payload.sessionId(), "upload", "kingdoms.xaero_archive.error.session");
            return;
        }
        if (!XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION.equals(payload.xaeroVersion())
                || !ArchiveHashing.isSha256(payload.checksum())) {
            fail(player, payload.sessionId(), "upload", "kingdoms.xaero_archive.error.version");
            return;
        }
        XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, payload.anchor(), payload.dimension());
        if (!access.allowed()) {
            fail(player, payload.sessionId(), "upload", access.errorKey());
            return;
        }
        try {
            validateMetadata(payload.regions(), payload.compressedSize(), payload.uncompressedSize(), payload.totalParts());
            XaeroArchiveStore.ArchiveLocation location = XaeroArchiveStore.location(
                    lifecycle.server(), access.factionId(), payload.dimension()
            );
            Path sessionRoot = location.root().resolve("sessions").resolve(player.getUUID().toString())
                    .resolve(payload.sessionId().toString()).normalize();
            if (!sessionRoot.startsWith(location.root().resolve("sessions").normalize())) {
                throw new IOException("Invalid Xaero upload session path");
            }
            UploadSession session = new UploadSession(
                    payload.sessionId(),
                    player.getUUID(),
                    payload.anchor(),
                    payload.dimension(),
                    access.factionId(),
                    location,
                    payload.regions(),
                    payload.compressedSize(),
                    payload.uncompressedSize(),
                    payload.totalParts(),
                    payload.checksum(),
                    sessionRoot,
                    lifecycle
            );
            if (!registerUpload(session)) {
                fail(player, payload.sessionId(), "upload", "kingdoms.xaero_archive.error.busy");
                return;
            }
            status(player, payload.sessionId(), "upload", 0, payload.compressedSize(), false, true,
                    "kingdoms.xaero_archive.status.uploading");
        } catch (IOException | IllegalArgumentException exception) {
            fail(player, payload.sessionId(), "upload", "kingdoms.xaero_archive.error.invalid_metadata");
        }
    }

    public static void uploadPart(ServerPlayer player, XaeroArchivePayloads.C2SUploadPart payload) {
        UploadSession session = UPLOADS.get(payload.sessionId());
        if (session == null || !session.playerId.equals(player.getUUID()) || !isLive(session.lifecycle)) {
            fail(player, payload.sessionId(), "upload", "kingdoms.xaero_archive.error.session");
            return;
        }
        try {
            session.accept(payload);
            status(player, payload.sessionId(), "upload", session.receivedBytes, session.compressedSize, false, true,
                    "kingdoms.xaero_archive.status.uploading");
        } catch (IOException | IllegalArgumentException exception) {
            cancelUpload(session);
            fail(player, payload.sessionId(), "upload", "kingdoms.xaero_archive.error.invalid_part");
        }
    }

    public static void finishUpload(ServerPlayer player, XaeroArchivePayloads.C2SFinishUpload payload) {
        UploadSession session = UPLOADS.get(payload.sessionId());
        if (session == null || !session.playerId.equals(player.getUUID()) || !isLive(session.lifecycle)
                || !ArchiveHashing.isSha256(payload.checksum())) {
            fail(player, payload.sessionId(), "merge", "kingdoms.xaero_archive.error.session");
            return;
        }
        XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, session.anchor, session.dimension);
        if (!access.allowed() || !session.factionId.equals(access.factionId())) {
            cancelUpload(session);
            fail(player, payload.sessionId(), "merge", access.allowed()
                    ? "kingdoms.xaero_archive.error.faction_changed" : access.errorKey());
            return;
        }
        if (!session.finishing.compareAndSet(false, true)) {
            fail(player, payload.sessionId(), "merge", "kingdoms.xaero_archive.error.session");
            return;
        }
        status(player, session.id, "merge", session.receivedBytes, session.compressedSize, false, true,
                "kingdoms.xaero_archive.status.merging");
        IO_EXECUTOR.execute(() -> {
            try {
                session.finish(payload.checksum());
                ArrayList<XaeroArchiveStore.IncomingRegion> incoming = new ArrayList<>(session.descriptors.size());
                for (ArchiveRegionDescriptor descriptor : session.descriptors) {
                    incoming.add(new XaeroArchiveStore.IncomingRegion(descriptor.name(), session.path(descriptor.name())));
                }
                executeIfLive(session.lifecycle, () -> authorizeUploadCommit(session, incoming));
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.warn("Xaero archive upload {} failed", session.id, exception);
                cancelUpload(session);
                sendStatusIfLive(session.lifecycle, session.playerId, session.id, "merge", 0, 0, true, false,
                        "kingdoms.xaero_archive.error.merge");
            }
        });
    }

    public static void requestDownload(ServerPlayer player, XaeroArchivePayloads.C2SRequestDownload payload) {
        ServerLifecycle lifecycle = lifecycle(player);
        if (lifecycle == null) {
            fail(player, payload.sessionId(), "download", "kingdoms.xaero_archive.error.session");
            return;
        }
        XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, payload.anchor(), payload.dimension());
        if (!access.allowed()) {
            fail(player, payload.sessionId(), "download", access.errorKey());
            return;
        }
        if (!reserveDownload(player.getUUID(), payload.sessionId())) {
            fail(player, payload.sessionId(), "download", "kingdoms.xaero_archive.error.busy");
            return;
        }
        XaeroArchiveStore.ArchiveLocation location;
        try {
            location = XaeroArchiveStore.location(lifecycle.server(), access.factionId(), payload.dimension());
        } catch (IOException exception) {
            DOWNLOAD_RESERVATIONS.remove(payload.sessionId(), player.getUUID());
            fail(player, payload.sessionId(), "download", "kingdoms.xaero_archive.error.read");
            return;
        }
        IO_EXECUTOR.execute(() -> {
            XaeroArchiveStore.Snapshot snapshot = null;
            try {
                snapshot = XaeroArchiveStore.snapshot(location);
                if (snapshot.regions().isEmpty()) {
                    throw new EmptyArchiveException();
                }
                List<ArchiveRegionDescriptor> descriptors = snapshot.regions().stream()
                        .map(XaeroArchiveStore.SnapshotRegion::descriptor).toList();
                long compressedSize = snapshot.compressedSize();
                long uncompressedSize = snapshot.uncompressedSize();
                int totalParts = expectedParts(descriptors);
                validateMetadata(descriptors, compressedSize, uncompressedSize, totalParts);
                String checksum = combinedChecksum(snapshot.regions());
                OutboundSession session = new OutboundSession(
                        payload.sessionId(), player.getUUID(), payload.anchor(), payload.dimension(), access.factionId(), location.serverIdentity(),
                        snapshot, compressedSize, uncompressedSize, totalParts, checksum, lifecycle
                );
                snapshot = null;
                executeWithLifecycle(
                        lifecycle,
                        () -> registerPreparedDownload(session, descriptors),
                        () -> {
                            DOWNLOAD_RESERVATIONS.remove(session.id, session.playerId);
                            session.close();
                        }
                );
            } catch (EmptyArchiveException exception) {
                DOWNLOAD_RESERVATIONS.remove(payload.sessionId(), player.getUUID());
                sendStatusIfLive(lifecycle, player.getUUID(), payload.sessionId(), "download", 0, 0, true, false,
                        "kingdoms.xaero_archive.error.no_data");
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.warn("Xaero archive download {} failed to start", payload.sessionId(), exception);
                DOWNLOAD_RESERVATIONS.remove(payload.sessionId(), player.getUUID());
                sendStatusIfLive(lifecycle, player.getUUID(), payload.sessionId(), "download", 0, 0, true, false,
                        "kingdoms.xaero_archive.error.read");
            } finally {
                if (snapshot != null) {
                    snapshot.close();
                }
            }
        });
    }

    public static void requestStats(ServerPlayer player, XaeroArchivePayloads.C2SRequestStats payload) {
        ServerLifecycle lifecycle = lifecycle(player);
        if (lifecycle == null) {
            stats(player, payload.requestId(), payload.dimension(), 0, 0, 0, 0, false,
                    "kingdoms.xaero_archive.error.session");
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = LAST_STATS_REQUEST.put(player.getUUID(), now);
        if (previous != null && now - previous < 1000L) {
            stats(player, payload.requestId(), payload.dimension(), 0, 0, 0, 0, false,
                    "kingdoms.xaero_archive.error.busy");
            return;
        }
        XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, payload.anchor(), payload.dimension());
        if (!access.allowed()) {
            stats(player, payload.requestId(), payload.dimension(), 0, 0, 0, 0, false, access.errorKey());
            return;
        }
        XaeroArchiveStore.ArchiveLocation location;
        try {
            location = XaeroArchiveStore.location(lifecycle.server(), access.factionId(), payload.dimension());
        } catch (IOException exception) {
            stats(player, payload.requestId(), payload.dimension(), 0, 0, 0, 0, false,
                    "kingdoms.xaero_archive.error.read");
            return;
        }
        IO_EXECUTOR.execute(() -> {
            try {
                XaeroArchiveManifest manifest = XaeroArchiveStore.load(location);
                long compressed = 0;
                long uncompressed = 0;
                int tiles = 0;
                for (ArchiveRegionDescriptor descriptor : manifest.regions()) {
                    compressed = Math.addExact(compressed, descriptor.compressedSize());
                    uncompressed = Math.addExact(uncompressed, descriptor.uncompressedSize());
                    tiles = Math.addExact(tiles, descriptor.tileCount());
                }
                long finalCompressed = compressed;
                long finalUncompressed = uncompressed;
                int finalTiles = tiles;
                executeIfLive(lifecycle, () -> completeStatsRequest(
                        lifecycle, player.getUUID(), payload, access.factionId(), finalCompressed, finalUncompressed,
                        manifest.regions().size(), finalTiles
                ));
            } catch (IOException | ArithmeticException exception) {
                sendStatsIfLive(lifecycle, player.getUUID(), payload.requestId(), payload.dimension(), 0, 0, 0, 0,
                        false, "kingdoms.xaero_archive.error.read");
            }
        });
    }

    public static void cancel(ServerPlayer player, UUID sessionId) {
        UploadSession upload = UPLOADS.get(sessionId);
        if (upload != null && upload.playerId.equals(player.getUUID())) {
            cancelUpload(upload);
        }
        OutboundSession download = DOWNLOADS.get(sessionId);
        if (download != null && download.playerId.equals(player.getUUID())) {
            DOWNLOADS.remove(sessionId, download);
            download.close();
        }
        DOWNLOAD_RESERVATIONS.remove(sessionId, player.getUUID());
        status(player, sessionId, "cancelled", 0, 0, true, false, "kingdoms.xaero_archive.status.cancelled");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.currentTimeMillis();
        for (UploadSession upload : List.copyOf(UPLOADS.values())) {
            if (!isLive(upload.lifecycle)
                    || !upload.finishing.get() && now - upload.lastActivity > XaeroArchiveLimits.SESSION_TIMEOUT_MILLIS) {
                cancelUpload(upload);
            }
        }
        MinecraftServer server = event.getServer();
        for (OutboundSession download : List.copyOf(DOWNLOADS.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(download.playerId);
            if (!isLive(download.lifecycle)
                    || player == null
                    || now - download.lastActivity > XaeroArchiveLimits.SESSION_TIMEOUT_MILLIS) {
                DOWNLOADS.remove(download.id, download);
                download.close();
                continue;
            }
            try {
                XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, download.anchor, download.dimension);
                if (!access.allowed() || !download.factionId.equals(access.factionId())) {
                    DOWNLOADS.remove(download.id, download);
                    download.close();
                    fail(player, download.id, "download", access.allowed()
                            ? "kingdoms.xaero_archive.error.faction_changed" : access.errorKey());
                    continue;
                }
                boolean complete = download.send(player, XaeroArchiveLimits.PARTS_PER_TICK);
                if (complete && DOWNLOADS.remove(download.id, download)) {
                    download.close();
                    status(player, download.id, "complete", download.compressedSize, download.compressedSize, true, true,
                            "kingdoms.xaero_archive.status.download_complete");
                }
            } catch (IOException exception) {
                DOWNLOADS.remove(download.id, download);
                download.close();
                fail(player, download.id, "download", "kingdoms.xaero_archive.error.read");
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        UPLOADS.values().stream().filter(session -> session.playerId.equals(playerId)).toList().forEach(XaeroArchiveSessions::cancelUpload);
        DOWNLOADS.values().stream().filter(session -> session.playerId.equals(playerId)).toList().forEach(session -> {
            DOWNLOADS.remove(session.id, session);
            session.close();
        });
        DOWNLOAD_RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
        LAST_STATS_REQUEST.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        synchronized (LIFECYCLE_COMMIT_LOCK) {
            activeServer = event.getServer();
            SERVER_GENERATION.incrementAndGet();
        }
        Path root = event.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .resolve("kingdoms").resolve("xaero_maps").normalize();
        IO_EXECUTOR.execute(() -> cleanupOrphanSessions(root));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        synchronized (LIFECYCLE_COMMIT_LOCK) {
            if (activeServer == event.getServer()) {
                activeServer = null;
                SERVER_GENERATION.incrementAndGet();
            }
        }
        UPLOADS.values().stream().toList().forEach(XaeroArchiveSessions::cancelUpload);
        DOWNLOADS.values().stream().toList().forEach(session -> {
            DOWNLOADS.remove(session.id, session);
            session.close();
        });
        DOWNLOAD_RESERVATIONS.clear();
        LAST_STATS_REQUEST.clear();
    }

    private static void authorizeUploadCommit(
            UploadSession session,
            List<XaeroArchiveStore.IncomingRegion> incoming
    ) {
        if (!isLive(session.lifecycle) || UPLOADS.get(session.id) != session || session.isCancelled()) {
            cancelUpload(session);
            return;
        }
        ServerPlayer player = session.lifecycle.server().getPlayerList().getPlayer(session.playerId);
        if (player == null) {
            cancelUpload(session);
            return;
        }
        XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, session.anchor, session.dimension);
        if (!access.allowed() || !session.factionId.equals(access.factionId())) {
            cancelUpload(session);
            fail(player, session.id, "merge", access.allowed()
                    ? "kingdoms.xaero_archive.error.faction_changed" : access.errorKey());
            return;
        }
        UUID token = session.grantCommit();
        if (token == null) {
            cancelUpload(session);
            fail(player, session.id, "merge", "kingdoms.xaero_archive.error.session");
            return;
        }
        IO_EXECUTOR.execute(() -> commitUpload(session, incoming, token));
    }

    private static void commitUpload(
            UploadSession session,
            List<XaeroArchiveStore.IncomingRegion> incoming,
            UUID token
    ) {
        try {
            synchronized (LIFECYCLE_COMMIT_LOCK) {
                if (!isLive(session.lifecycle) || UPLOADS.get(session.id) != session || !session.consumeCommit(token)) {
                    return;
                }
                XaeroArchiveStore.merge(session.location, incoming);
            }
            sendStatusIfLive(
                    session.lifecycle,
                    session.playerId,
                    session.id,
                    "complete",
                    session.compressedSize,
                    session.compressedSize,
                    true,
                    true,
                    "kingdoms.xaero_archive.status.upload_complete"
            );
        } catch (IOException | RuntimeException exception) {
            KalFactions.LOGGER.warn("Xaero archive upload {} failed to commit", session.id, exception);
            sendStatusIfLive(session.lifecycle, session.playerId, session.id, "merge", 0, 0, true, false,
                    "kingdoms.xaero_archive.error.merge");
        } finally {
            cancelUpload(session);
        }
    }

    private static void registerPreparedDownload(
            OutboundSession session,
            List<ArchiveRegionDescriptor> descriptors
    ) {
        try {
            if (!isLive(session.lifecycle)
                    || !session.playerId.equals(DOWNLOAD_RESERVATIONS.get(session.id))) {
                session.close();
                return;
            }
            ServerPlayer player = session.lifecycle.server().getPlayerList().getPlayer(session.playerId);
            if (player == null) {
                session.close();
                return;
            }
            XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, session.anchor, session.dimension);
            if (!access.allowed() || !session.factionId.equals(access.factionId())) {
                session.close();
                fail(player, session.id, "download", access.allowed()
                        ? "kingdoms.xaero_archive.error.faction_changed" : access.errorKey());
                return;
            }
            if (DOWNLOADS.putIfAbsent(session.id, session) != null) {
                session.close();
                fail(player, session.id, "download", "kingdoms.xaero_archive.error.busy");
                return;
            }
            session.startPreparing();
            PacketDistributor.sendToPlayer(player, new XaeroArchivePayloads.S2CBeginDownload(
                    session.id,
                    session.serverIdentity,
                    session.dimension,
                    session.factionId,
                    session.compressedSize,
                    session.uncompressedSize,
                    session.totalParts,
                    session.checksum,
                    descriptors
            ));
        } finally {
            DOWNLOAD_RESERVATIONS.remove(session.id, session.playerId);
        }
    }

    private static void completeStatsRequest(
            ServerLifecycle lifecycle,
            UUID playerId,
            XaeroArchivePayloads.C2SRequestStats payload,
            UUID factionId,
            long compressed,
            long uncompressed,
            int regions,
            int tiles
    ) {
        if (!isLive(lifecycle)) {
            return;
        }
        ServerPlayer player = lifecycle.server().getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        XaeroArchiveAccess.AccessResult access = XaeroArchiveAccess.authorize(player, payload.anchor(), payload.dimension());
        if (!access.allowed() || !factionId.equals(access.factionId())) {
            stats(player, payload.requestId(), payload.dimension(), 0, 0, 0, 0, false,
                    access.allowed() ? "kingdoms.xaero_archive.error.faction_changed" : access.errorKey());
            return;
        }
        stats(player, payload.requestId(), payload.dimension(), compressed, uncompressed, regions, tiles, true,
                "kingdoms.xaero_archive.status.ready");
    }

    private static ServerLifecycle lifecycle(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || activeServer != server
                || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return null;
        }
        return new ServerLifecycle(server, SERVER_GENERATION.get());
    }

    private static boolean isLive(ServerLifecycle lifecycle) {
        return lifecycle != null
                && activeServer == lifecycle.server()
                && SERVER_GENERATION.get() == lifecycle.generation();
    }

    private static void executeIfLive(ServerLifecycle lifecycle, Runnable action) {
        executeWithLifecycle(lifecycle, action, () -> {
        });
    }

    private static void executeWithLifecycle(ServerLifecycle lifecycle, Runnable action, Runnable stale) {
        if (!isLive(lifecycle)) {
            stale.run();
            return;
        }
        lifecycle.server().execute(() -> {
            if (isLive(lifecycle)) {
                action.run();
            } else {
                stale.run();
            }
        });
    }

    private static void sendStatusIfLive(
            ServerLifecycle lifecycle,
            UUID playerId,
            UUID sessionId,
            String phase,
            long completed,
            long total,
            boolean terminal,
            boolean successful,
            String messageKey
    ) {
        executeIfLive(lifecycle, () -> {
            ServerPlayer player = lifecycle.server().getPlayerList().getPlayer(playerId);
            if (player != null) {
                status(player, sessionId, phase, completed, total, terminal, successful, messageKey);
            }
        });
    }

    private static void sendStatsIfLive(
            ServerLifecycle lifecycle,
            UUID playerId,
            UUID requestId,
            ResourceLocation dimension,
            long compressed,
            long uncompressed,
            int regions,
            int tiles,
            boolean successful,
            String messageKey
    ) {
        executeIfLive(lifecycle, () -> {
            ServerPlayer player = lifecycle.server().getPlayerList().getPlayer(playerId);
            if (player != null) {
                stats(player, requestId, dimension, compressed, uncompressed, regions, tiles, successful, messageKey);
            }
        });
    }

    private static boolean hasCapacity(UUID playerId) {
        long playerSessions = UPLOADS.values().stream().filter(session -> session.playerId.equals(playerId)).count()
                + DOWNLOADS.values().stream().filter(session -> session.playerId.equals(playerId)).count()
                + DOWNLOAD_RESERVATIONS.values().stream().filter(playerId::equals).count();
        return playerSessions < XaeroArchiveLimits.MAX_PLAYER_SESSIONS
                && UPLOADS.size() + DOWNLOADS.size() + DOWNLOAD_RESERVATIONS.size() < XaeroArchiveLimits.MAX_GLOBAL_SESSIONS;
    }

    private static boolean reserveDownload(UUID playerId, UUID sessionId) {
        synchronized (CAPACITY_LOCK) {
            if (!hasCapacity(playerId)
                    || UPLOADS.containsKey(sessionId)
                    || DOWNLOADS.containsKey(sessionId)
                    || DOWNLOAD_RESERVATIONS.containsKey(sessionId)) {
                return false;
            }
            DOWNLOAD_RESERVATIONS.put(sessionId, playerId);
            return true;
        }
    }

    private static boolean registerUpload(UploadSession session) {
        synchronized (CAPACITY_LOCK) {
            if (!hasCapacity(session.playerId)
                    || UPLOADS.containsKey(session.id)
                    || DOWNLOADS.containsKey(session.id)
                    || DOWNLOAD_RESERVATIONS.containsKey(session.id)) {
                return false;
            }
            UPLOADS.put(session.id, session);
            return true;
        }
    }

    private static void validateMetadata(
            List<ArchiveRegionDescriptor> descriptors,
            long compressedSize,
            long uncompressedSize,
            int totalParts
    ) {
        if (descriptors.isEmpty() || descriptors.size() > XaeroArchiveLimits.MAX_REGIONS
                || compressedSize <= 0 || compressedSize > XaeroArchiveLimits.MAX_SESSION_COMPRESSED_SIZE
                || uncompressedSize <= 0 || uncompressedSize > XaeroArchiveLimits.MAX_SESSION_UNCOMPRESSED_SIZE
                || totalParts <= 0 || totalParts > XaeroArchiveLimits.MAX_PARTS) {
            throw new IllegalArgumentException("Invalid Xaero archive metadata limits");
        }
        long compressedSum = 0;
        long uncompressedSum = 0;
        String previousName = null;
        for (ArchiveRegionDescriptor descriptor : descriptors) {
            if (previousName != null && previousName.compareTo(descriptor.name()) >= 0) {
                throw new IllegalArgumentException("Xaero archive descriptors must be unique and sorted");
            }
            compressedSum = Math.addExact(compressedSum, descriptor.compressedSize());
            uncompressedSum = Math.addExact(uncompressedSum, descriptor.uncompressedSize());
            previousName = descriptor.name();
        }
        if (compressedSum != compressedSize || uncompressedSum != uncompressedSize || expectedParts(descriptors) != totalParts) {
            throw new IllegalArgumentException("Xaero archive metadata totals do not match");
        }
    }

    private static int expectedParts(List<ArchiveRegionDescriptor> descriptors) {
        long parts = 0;
        for (ArchiveRegionDescriptor descriptor : descriptors) {
            parts = Math.addExact(parts, Math.floorDiv(descriptor.compressedSize() + XaeroArchiveLimits.PART_SIZE - 1,
                    XaeroArchiveLimits.PART_SIZE));
        }
        if (parts > XaeroArchiveLimits.MAX_PARTS) {
            throw new IllegalArgumentException("Xaero archive has too many parts");
        }
        return Math.toIntExact(parts);
    }

    private static String combinedChecksum(List<XaeroArchiveStore.SnapshotRegion> regions) throws IOException {
        MessageDigest digest = ArchiveHashing.sha256();
        byte[] buffer = new byte[32 * 1024];
        for (XaeroArchiveStore.SnapshotRegion region : regions) {
            try (InputStream input = Files.newInputStream(region.path())) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
        }
        return ArchiveHashing.hex(digest.digest());
    }

    private static void cancelUpload(UploadSession session) {
        UPLOADS.remove(session.id, session);
        session.cancel();
    }

    private static void cleanup(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    KalFactions.LOGGER.warn("Could not clean Xaero archive temporary path {}", path, exception);
                }
            });
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Could not enumerate Xaero archive temporary path {}", root, exception);
        }
    }

    private static void cleanupOrphanSessions(Path archiveRoot) {
        if (!Files.isDirectory(archiveRoot)) {
            return;
        }
        try (var paths = Files.find(
                archiveRoot,
                5,
                (path, attributes) -> attributes.isDirectory()
                        && path.getFileName() != null
                        && path.getFileName().toString().equals("sessions")
                        && path.toAbsolutePath().normalize().startsWith(archiveRoot)
        )) {
            paths.toList().forEach(XaeroArchiveSessions::cleanup);
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Could not clean orphaned Xaero archive sessions under {}", archiveRoot, exception);
        }
    }

    private static void status(
            ServerPlayer player,
            UUID sessionId,
            String phase,
            long completed,
            long total,
            boolean terminal,
            boolean successful,
            String messageKey
    ) {
        PacketDistributor.sendToPlayer(player, new XaeroArchivePayloads.S2CStatus(
                sessionId, phase, completed, total, terminal, successful, messageKey
        ));
    }

    private static void fail(ServerPlayer player, UUID sessionId, String phase, String messageKey) {
        status(player, sessionId, phase, 0, 0, true, false, messageKey);
    }

    private static void stats(
            ServerPlayer player,
            UUID requestId,
            ResourceLocation dimension,
            long compressed,
            long uncompressed,
            int regions,
            int tiles,
            boolean successful,
            String messageKey
    ) {
        PacketDistributor.sendToPlayer(player, new XaeroArchivePayloads.S2CStats(
                requestId, dimension, compressed, uncompressed, regions, tiles, successful, messageKey
        ));
    }

    private static final class UploadSession {
        private final UUID id;
        private final UUID playerId;
        private final net.minecraft.core.BlockPos anchor;
        private final ResourceLocation dimension;
        private final UUID factionId;
        private final XaeroArchiveStore.ArchiveLocation location;
        private final List<ArchiveRegionDescriptor> descriptors;
        private final long compressedSize;
        private final long uncompressedSize;
        private final int totalParts;
        private final String checksum;
        private final Path root;
        private final ServerLifecycle lifecycle;
        private final MessageDigest digest = ArchiveHashing.sha256();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final long startedAt = System.currentTimeMillis();
        private volatile long lastActivity = startedAt;
        private int expectedSequence;
        private int expectedRegion;
        private long expectedOffset;
        private long receivedBytes;
        private int queuedParts;
        private volatile boolean cancelled;
        private UUID commitToken;
        private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

        private UploadSession(
                UUID id,
                UUID playerId,
                net.minecraft.core.BlockPos anchor,
                ResourceLocation dimension,
                UUID factionId,
                XaeroArchiveStore.ArchiveLocation location,
                List<ArchiveRegionDescriptor> descriptors,
                long compressedSize,
                long uncompressedSize,
                int totalParts,
                String checksum,
                Path root,
                ServerLifecycle lifecycle
        ) {
            this.id = id;
            this.playerId = playerId;
            this.anchor = anchor.immutable();
            this.dimension = dimension;
            this.factionId = factionId;
            this.location = location;
            this.descriptors = List.copyOf(descriptors);
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.totalParts = totalParts;
            this.checksum = checksum;
            this.root = root;
            this.lifecycle = lifecycle;
        }

        private synchronized void accept(XaeroArchivePayloads.C2SUploadPart part) throws IOException {
            if (finishing.get()
                    || part.sequence() != expectedSequence
                    || part.totalParts() != totalParts
                    || part.regionIndex() != expectedRegion
                    || expectedRegion >= descriptors.size()
                    || part.offset() != expectedOffset
                    || part.data().length == 0
                    || part.data().length > XaeroArchiveLimits.PART_SIZE
                    || !ArchiveHashing.sha256(part.data()).equals(part.checksum())
                    || queuedParts >= 32) {
                throw new IOException("Unexpected Xaero upload part");
            }
            long elapsed = Math.max(0, System.currentTimeMillis() - startedAt);
            long allowed = 512L * 1024L + elapsed * XaeroArchiveLimits.UPLOAD_BYTES_PER_SECOND / 1000L;
            if (receivedBytes + part.data().length > allowed) {
                throw new IOException("Xaero upload rate exceeded");
            }
            ArchiveRegionDescriptor descriptor = descriptors.get(expectedRegion);
            int expectedLength = (int) Math.min(XaeroArchiveLimits.PART_SIZE, descriptor.compressedSize() - expectedOffset);
            if (part.data().length != expectedLength) {
                throw new IOException("Unexpected Xaero upload part length");
            }
            int region = expectedRegion;
            long offset = expectedOffset;
            byte[] data = part.data();
            queuedParts++;
            writes = writes.thenRunAsync(() -> writePart(region, offset, data), IO_EXECUTOR)
                    .whenComplete((ignored, throwable) -> {
                        synchronized (UploadSession.this) {
                            queuedParts--;
                        }
                    });
            digest.update(part.data());
            expectedOffset += part.data().length;
            receivedBytes += part.data().length;
            expectedSequence++;
            if (expectedOffset == descriptor.compressedSize()) {
                expectedRegion++;
                expectedOffset = 0;
            }
            lastActivity = System.currentTimeMillis();
        }

        private void finish(String finishChecksum) throws IOException {
            if (expectedSequence != totalParts
                    || expectedRegion != descriptors.size()
                    || receivedBytes != compressedSize
                    || !checksum.equals(finishChecksum)
                    || !checksum.equals(ArchiveHashing.hex(digest.digest()))) {
                throw new IOException("Incomplete Xaero upload session");
            }
            CompletableFuture<Void> pendingWrites = writes;
            try {
                pendingWrites.join();
            } catch (CompletionException exception) {
                throw new IOException("Could not write Xaero upload staging", exception.getCause());
            }
            if (cancelled) {
                throw new IOException("Xaero upload was cancelled");
            }
            for (ArchiveRegionDescriptor descriptor : descriptors) {
                Path path = path(descriptor.name());
                if (Files.size(path) != descriptor.compressedSize()
                        || !ArchiveHashing.sha256(path).equals(descriptor.checksum())) {
                    throw new IOException("Xaero upload region checksum mismatch");
                }
                XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.inspect(path);
                if (stats.uncompressedSize() != descriptor.uncompressedSize() || stats.tileCount() != descriptor.tileCount()) {
                    throw new IOException("Xaero upload region metadata mismatch");
                }
            }
        }

        private void writePart(int regionIndex, long offset, byte[] data) {
            if (cancelled) {
                return;
            }
            try {
                Path destination = path(descriptors.get(regionIndex).name());
                Files.createDirectories(root);
                try (var channel = java.nio.channels.FileChannel.open(
                        destination,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                )) {
                    if (channel.size() != offset) {
                        throw new IOException("Xaero upload staging size mismatch");
                    }
                    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(data);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer, offset + buffer.position());
                    }
                }
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }

        private synchronized void cancel() {
            cancelled = true;
            commitToken = null;
            finishing.set(true);
            writes.whenCompleteAsync((ignored, throwable) -> cleanup(root), IO_EXECUTOR);
        }

        private synchronized boolean isCancelled() {
            return cancelled;
        }

        private synchronized UUID grantCommit() {
            if (cancelled || commitToken != null) {
                return null;
            }
            commitToken = UUID.randomUUID();
            return commitToken;
        }

        private synchronized boolean consumeCommit(UUID token) {
            if (cancelled || commitToken == null || !commitToken.equals(token)) {
                return false;
            }
            commitToken = null;
            return true;
        }

        private Path path(String name) throws IOException {
            if (!ArchiveRegionDescriptor.isSafeName(name)) {
                throw new IOException("Unsafe Xaero upload region name");
            }
            Path result = root.resolve(name).normalize();
            if (!result.startsWith(root)) {
                throw new IOException("Xaero upload path traversal");
            }
            return result;
        }
    }

    private static final class OutboundSession {
        private final UUID id;
        private final UUID playerId;
        private final net.minecraft.core.BlockPos anchor;
        private final ResourceLocation dimension;
        private final UUID factionId;
        private final String serverIdentity;
        private final XaeroArchiveStore.Snapshot snapshot;
        private final List<XaeroArchiveStore.SnapshotRegion> regions;
        private final long compressedSize;
        private final long uncompressedSize;
        private final int totalParts;
        private final String checksum;
        private final ServerLifecycle lifecycle;
        private volatile long lastActivity = System.currentTimeMillis();
        private int sequence;
        private int regionIndex;
        private long regionOffset;
        private long sentBytes;
        private InputStream input;
        private final ArrayBlockingQueue<PreparedPart> prepared = new ArrayBlockingQueue<>(16);
        private volatile boolean preparationComplete;
        private volatile IOException preparationFailure;
        private volatile boolean closed;
        private CompletableFuture<Void> preparation;

        private OutboundSession(
                UUID id,
                UUID playerId,
                net.minecraft.core.BlockPos anchor,
                ResourceLocation dimension,
                UUID factionId,
                String serverIdentity,
                XaeroArchiveStore.Snapshot snapshot,
                long compressedSize,
                long uncompressedSize,
                int totalParts,
                String checksum,
                ServerLifecycle lifecycle
        ) {
            this.id = id;
            this.playerId = playerId;
            this.anchor = anchor.immutable();
            this.dimension = dimension;
            this.factionId = factionId;
            this.serverIdentity = serverIdentity;
            this.snapshot = snapshot;
            this.regions = snapshot.regions();
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.totalParts = totalParts;
            this.checksum = checksum;
            this.lifecycle = lifecycle;
        }

        private void startPreparing() {
            preparation = CompletableFuture.runAsync(this::prepare, IO_EXECUTOR);
        }

        private void prepare() {
            int preparedSequence = 0;
            try {
                for (int preparedRegion = 0; preparedRegion < regions.size() && !closed; preparedRegion++) {
                    XaeroArchiveStore.SnapshotRegion region = regions.get(preparedRegion);
                    long offset = 0;
                    try (InputStream source = Files.newInputStream(region.path())) {
                        while (offset < region.descriptor().compressedSize() && !closed) {
                            int length = (int) Math.min(XaeroArchiveLimits.PART_SIZE,
                                    region.descriptor().compressedSize() - offset);
                            byte[] data = source.readNBytes(length);
                            if (data.length != length) {
                                throw new IOException("Truncated Xaero archive blob");
                            }
                            prepared.put(new PreparedPart(preparedSequence++, preparedRegion, offset, data));
                            offset += data.length;
                        }
                    }
                }
                if (!closed && preparedSequence != totalParts) {
                    throw new IOException("Xaero archive part preparation mismatch");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                preparationFailure = exception;
            } finally {
                preparationComplete = true;
            }
        }

        private boolean send(ServerPlayer player, int budget) throws IOException {
            if (preparationFailure != null) {
                throw preparationFailure;
            }
            for (int part = 0; part < budget; part++) {
                PreparedPart next = prepared.poll();
                if (next == null) {
                    break;
                }
                PacketDistributor.sendToPlayer(player, new XaeroArchivePayloads.S2CDownloadPart(
                        id, next.sequence, totalParts, next.regionIndex, next.offset,
                        ArchiveHashing.sha256(next.data), next.data
                ));
                sequence++;
                sentBytes += next.data.length;
                lastActivity = System.currentTimeMillis();
            }
            return preparationComplete && prepared.isEmpty() && sequence == totalParts && sentBytes == compressedSize;
        }

        private void close() {
            closed = true;
            prepared.clear();
            if (preparation != null) {
                preparation.cancel(true);
            }
            IO_EXECUTOR.execute(snapshot::close);
        }

        private record PreparedPart(int sequence, int regionIndex, long offset, byte[] data) {
        }
    }

    private static final class EmptyArchiveException extends IOException {
    }

    private record ServerLifecycle(MinecraftServer server, long generation) {
    }

    private XaeroArchiveSessions() {
    }
}
