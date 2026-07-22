package com.geydev.kalfactions.integration.xaero.archive.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveRegionDescriptor;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveLimits;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchivePayloads;
import com.geydev.kalfactions.integration.xaero.archive.XaeroRegionCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class XaeroArchiveClient {
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kingdoms-xaero-client");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile TransferState state = TransferState.idle();
    private static volatile Consumer<TransferState> listener = ignored -> {
    };
    private static UploadSender upload;
    private static UUID pendingUploadId;
    private static DownloadReceiver download;
    private static PendingDownload pendingDownload;
    private static boolean finishingDownload;
    private static StatsRequest statsRequest;

    public static void setListener(Consumer<TransferState> newListener) {
        listener = newListener == null ? ignored -> {
        } : newListener;
        listener.accept(state);
    }

    public static TransferState state() {
        return state;
    }

    public static void startUpload(BlockPos anchor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (busy() || minecraft.level == null) {
            publish(TransferState.error("kingdoms.xaero_archive.error.busy"));
            return;
        }
        XaeroWorldMapArchiveCompat.Compatibility compatibility = XaeroWorldMapArchiveCompat.compatibility();
        if (!compatibility.compatible()) {
            publish(TransferState.error(compatibility.errorKey()));
            return;
        }
        UUID sessionId = UUID.randomUUID();
        pendingUploadId = sessionId;
        ResourceLocation dimension = minecraft.level.dimension().location();
        publish(new TransferState(sessionId, "snapshot", 0, 0, 0, 0, false, false,
                "kingdoms.xaero_archive.status.snapshot"));
        XaeroWorldMapArchiveCompat.snapshot(sessionId, dimension, IO_EXECUTOR, snapshot -> {
            if (!sessionId.equals(pendingUploadId)) {
                cleanupAsync(snapshot.root());
                return;
            }
            pendingUploadId = null;
            try {
                upload = new UploadSender(anchor.immutable(), snapshot);
                PacketDistributor.sendToServer(upload.beginPayload());
                publish(new TransferState(sessionId, "upload", 0, snapshot.compressedSize(),
                        snapshot.regions().size(), snapshot.tileCount(), false, false,
                        "kingdoms.xaero_archive.status.uploading"));
            } catch (RuntimeException exception) {
                cleanupAsync(snapshot.root());
                publish(TransferState.error("kingdoms.xaero_archive.error.invalid_metadata"));
            }
        }, throwable -> {
            if (sessionId.equals(pendingUploadId)) {
                pendingUploadId = null;
                publish(TransferState.error(errorKey(throwable, "kingdoms.xaero_archive.error.snapshot")));
            }
        });
    }

    public static void startDownload(BlockPos anchor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (busy() || minecraft.level == null) {
            publish(TransferState.error("kingdoms.xaero_archive.error.busy"));
            return;
        }
        XaeroWorldMapArchiveCompat.Compatibility compatibility = XaeroWorldMapArchiveCompat.compatibility();
        if (!compatibility.compatible()) {
            publish(TransferState.error(compatibility.errorKey()));
            return;
        }
        UUID sessionId = UUID.randomUUID();
        ResourceLocation dimension = minecraft.level.dimension().location();
        pendingDownload = new PendingDownload(sessionId, anchor.immutable(), dimension);
        publish(new TransferState(sessionId, "download", 0, 0, 0, 0, false, false,
                "kingdoms.xaero_archive.status.preparing"));
        PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SRequestDownload(sessionId, anchor, dimension));
    }

    public static void requestStats(BlockPos anchor, Consumer<ArchiveStats> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            consumer.accept(new ArchiveStats(DataStats.empty(), DataStats.empty(),
                    "kingdoms.xaero_archive.error.unavailable"));
            return;
        }
        ResourceLocation dimension = minecraft.level.dimension().location();
        UUID requestId = UUID.randomUUID();
        StatsRequest request = new StatsRequest(requestId, dimension, consumer);
        statsRequest = request;
        PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SRequestStats(requestId, anchor, dimension));
        XaeroWorldMapArchiveCompat.inspectLocal(dimension, IO_EXECUTOR, local -> {
            StatsRequest current = statsRequest;
            if (current != null && current.id.equals(requestId)) {
                current.local = new DataStats(local.compressedSize(), local.uncompressedSize(),
                        local.regionCount(), local.tileCount());
                completeStats(current);
            }
        }, throwable -> {
            StatsRequest current = statsRequest;
            if (current != null && current.id.equals(requestId)) {
                current.local = DataStats.empty();
                current.errorKey = errorKey(throwable, "kingdoms.xaero_archive.error.snapshot");
                completeStats(current);
            }
        });
    }

    public static void cancel() {
        UUID sessionId = state.sessionId();
        if (sessionId != null) {
            PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SCancel(sessionId));
        }
        clearTransfers();
        publish(new TransferState(sessionId, "cancelled", 0, 0, 0, 0, true, false,
                "kingdoms.xaero_archive.status.cancelled"));
    }

    public static void handleBeginDownload(XaeroArchivePayloads.S2CBeginDownload payload) {
        PendingDownload pending = pendingDownload;
        if (pending == null || !pending.sessionId.equals(payload.sessionId()) || !pending.dimension.equals(payload.dimension())) {
            PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SCancel(payload.sessionId()));
            return;
        }
        try {
            validateMetadata(payload.regions(), payload.compressedSize(), payload.uncompressedSize(), payload.totalParts());
            if (!ArchiveHashing.isSha256(payload.checksum()) || !payload.serverIdentity().matches("[0-9a-f]{32}")) {
                throw new IOException("Invalid Xaero archive identity");
            }
            Path root = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
                    .resolve("kingdoms").resolve("xaero_transfers").resolve("download-" + payload.sessionId()).normalize();
            download = new DownloadReceiver(payload, root);
            pendingDownload = null;
            publish(new TransferState(payload.sessionId(), "download", 0, payload.compressedSize(),
                    payload.regions().size(), payload.regions().stream().mapToInt(ArchiveRegionDescriptor::tileCount).sum(),
                    false, false, "kingdoms.xaero_archive.status.downloading"));
        } catch (IOException | RuntimeException exception) {
            PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SCancel(payload.sessionId()));
            publish(TransferState.error("kingdoms.xaero_archive.error.invalid_metadata"));
        }
    }

    public static void handleDownloadPart(XaeroArchivePayloads.S2CDownloadPart payload) {
        DownloadReceiver receiver = download;
        if (receiver == null || !receiver.id.equals(payload.sessionId())) {
            return;
        }
        try {
            boolean complete = receiver.accept(payload);
            publish(new TransferState(receiver.id, "download", receiver.receivedBytes, receiver.compressedSize,
                    receiver.descriptors.size(), receiver.tileCount, false, false,
                    "kingdoms.xaero_archive.status.downloading"));
            if (complete) {
                download = null;
                finishingDownload = true;
                receiver.finishAsync(XaeroArchiveClient::importVerified, throwable -> {
                    finishingDownload = false;
                    receiver.cleanup();
                    publish(TransferState.error(errorKey(throwable, "kingdoms.xaero_archive.error.invalid_part")));
                });
            }
        } catch (IOException | RuntimeException exception) {
            PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SCancel(receiver.id));
            receiver.cleanup();
            download = null;
            publish(TransferState.error("kingdoms.xaero_archive.error.invalid_part"));
        }
    }

    public static void handleStatus(XaeroArchivePayloads.S2CStatus payload) {
        if (state.sessionId() != null && !state.sessionId().equals(payload.sessionId())) {
            return;
        }
        if ((download != null || finishingDownload) && payload.successful() && payload.terminal()) {
            return;
        }
        publish(new TransferState(payload.sessionId(), payload.phase(), payload.completed(), payload.total(),
                state.regionCount(), state.tileCount(), payload.terminal(), payload.successful(), payload.messageKey()));
        if (payload.terminal()) {
            if (upload != null && upload.id.equals(payload.sessionId())) {
                upload.cleanup();
                upload = null;
            }
            if (!payload.successful()) {
                if (download != null) {
                    download.cleanup();
                    download = null;
                }
                pendingDownload = null;
            }
        }
    }

    public static void handleStats(XaeroArchivePayloads.S2CStats payload) {
        StatsRequest request = statsRequest;
        if (request == null || !request.id.equals(payload.requestId()) || !request.dimension.equals(payload.dimension())) {
            return;
        }
        if (payload.compressedSize() < 0 || payload.compressedSize() > XaeroArchiveLimits.MAX_SESSION_COMPRESSED_SIZE
                || payload.uncompressedSize() < 0 || payload.uncompressedSize() > XaeroArchiveLimits.MAX_SESSION_UNCOMPRESSED_SIZE
                || payload.regionCount() < 0 || payload.regionCount() > XaeroArchiveLimits.MAX_REGIONS
                || payload.tileCount() < 0 || payload.tileCount() > XaeroArchiveLimits.MAX_REGIONS * 1024) {
            request.faction = DataStats.empty();
            request.errorKey = "kingdoms.xaero_archive.error.invalid_metadata";
        } else {
            request.faction = new DataStats(payload.compressedSize(), payload.uncompressedSize(),
                    payload.regionCount(), payload.tileCount());
            if (!payload.successful()) {
                request.errorKey = payload.messageKey();
            }
        }
        completeStats(request);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        UploadSender sender = upload;
        if (sender == null || sender.finishedSending) {
            return;
        }
        try {
            sender.send(2);
            publish(new TransferState(sender.id, "upload", sender.sentBytes, sender.snapshot.compressedSize(),
                    sender.snapshot.regions().size(), sender.snapshot.tileCount(), false, false,
                    "kingdoms.xaero_archive.status.uploading"));
        } catch (IOException exception) {
            PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SCancel(sender.id));
            sender.cleanup();
            upload = null;
            publish(TransferState.error("kingdoms.xaero_archive.error.read"));
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearTransfers();
        publish(TransferState.idle());
    }

    private static boolean busy() {
        return upload != null || pendingUploadId != null || download != null || pendingDownload != null
                || finishingDownload || state.phase().equals("snapshot") || state.phase().equals("import");
    }

    private static void clearTransfers() {
        if (upload != null) {
            upload.cleanup();
            upload = null;
        }
        pendingUploadId = null;
        if (download != null) {
            download.cleanup();
            download = null;
        }
        pendingDownload = null;
        finishingDownload = false;
        statsRequest = null;
    }

    private static void publish(TransferState next) {
        state = next;
        listener.accept(next);
    }

    private static void completeStats(StatsRequest request) {
        if (request.local == null || request.faction == null || statsRequest != request) {
            return;
        }
        statsRequest = null;
        request.consumer.accept(new ArchiveStats(request.local, request.faction, request.errorKey));
    }

    private static void importVerified(XaeroWorldMapArchiveCompat.Download importData) {
        publish(new TransferState(importData.sessionId(), "import", importData.compressedSize(),
                importData.compressedSize(), importData.regions().size(), importData.tileCount(), false, false,
                "kingdoms.xaero_archive.status.importing"));
        XaeroWorldMapArchiveCompat.importRegions(importData, IO_EXECUTOR, () -> {
            finishingDownload = false;
            cleanupAsync(importData.root());
            publish(new TransferState(importData.sessionId(), "complete", importData.compressedSize(),
                    importData.compressedSize(), importData.regions().size(), importData.tileCount(), true, true,
                    "kingdoms.xaero_archive.status.import_complete"));
        }, throwable -> {
            finishingDownload = false;
            cleanupAsync(importData.root());
            publish(TransferState.error(errorKey(throwable, "kingdoms.xaero_archive.error.import")));
        });
    }

    private static void cleanupAsync(Path root) {
        IO_EXECUTOR.execute(() -> XaeroWorldMapArchiveCompat.cleanup(root));
    }

    private static String errorKey(Throwable throwable, String fallback) {
        String message = throwable == null ? null : throwable.getMessage();
        return message != null && message.startsWith("kingdoms.xaero_archive.") ? message : fallback;
    }

    private static void validateMetadata(
            List<ArchiveRegionDescriptor> descriptors,
            long compressed,
            long uncompressed,
            int totalParts
    ) throws IOException {
        if (descriptors.isEmpty() || descriptors.size() > XaeroArchiveLimits.MAX_REGIONS
                || compressed <= 0 || compressed > XaeroArchiveLimits.MAX_SESSION_COMPRESSED_SIZE
                || uncompressed <= 0 || uncompressed > XaeroArchiveLimits.MAX_SESSION_UNCOMPRESSED_SIZE
                || totalParts <= 0 || totalParts > XaeroArchiveLimits.MAX_PARTS) {
            throw new IOException("Xaero archive metadata exceeds limits");
        }
        long compressedSum = 0;
        long uncompressedSum = 0;
        long parts = 0;
        String previous = null;
        for (ArchiveRegionDescriptor descriptor : descriptors) {
            if (previous != null && previous.compareTo(descriptor.name()) >= 0) {
                throw new IOException("Xaero archive descriptors are not sorted");
            }
            compressedSum = Math.addExact(compressedSum, descriptor.compressedSize());
            uncompressedSum = Math.addExact(uncompressedSum, descriptor.uncompressedSize());
            parts = Math.addExact(parts, Math.floorDiv(descriptor.compressedSize() + XaeroArchiveLimits.PART_SIZE - 1,
                    XaeroArchiveLimits.PART_SIZE));
            previous = descriptor.name();
        }
        if (compressedSum != compressed || uncompressedSum != uncompressed || parts != totalParts) {
            throw new IOException("Xaero archive metadata totals mismatch");
        }
    }

    public record TransferState(
            UUID sessionId,
            String phase,
            long completedBytes,
            long totalBytes,
            int regionCount,
            int tileCount,
            boolean terminal,
            boolean successful,
            String messageKey
    ) {
        public static TransferState idle() {
            return new TransferState(null, "idle", 0, 0, 0, 0, true, true,
                    "kingdoms.xaero_archive.status.idle");
        }

        public static TransferState error(String key) {
            return new TransferState(null, "error", 0, 0, 0, 0, true, false, key);
        }

        public double progress() {
            return totalBytes <= 0 ? 0.0D : Math.min(1.0D, (double) completedBytes / (double) totalBytes);
        }
    }

    private record PendingDownload(UUID sessionId, BlockPos anchor, ResourceLocation dimension) {
    }

    public record DataStats(long compressedSize, long uncompressedSize, int regionCount, int tileCount) {
        private static DataStats empty() {
            return new DataStats(0, 0, 0, 0);
        }
    }

    public record ArchiveStats(DataStats local, DataStats faction, String messageKey) {
    }

    private static final class StatsRequest {
        private final UUID id;
        private final ResourceLocation dimension;
        private final Consumer<ArchiveStats> consumer;
        private DataStats local;
        private DataStats faction;
        private String errorKey = "";

        private StatsRequest(UUID id, ResourceLocation dimension, Consumer<ArchiveStats> consumer) {
            this.id = id;
            this.dimension = dimension;
            this.consumer = consumer;
        }
    }

    private static final class UploadSender {
        private final UUID id;
        private final BlockPos anchor;
        private final XaeroWorldMapArchiveCompat.Snapshot snapshot;
        private final int totalParts;
        private int sequence;
        private long sentBytes;
        private final ArrayBlockingQueue<UploadPart> prepared = new ArrayBlockingQueue<>(16);
        private volatile boolean preparationComplete;
        private volatile IOException preparationFailure;
        private volatile boolean closed;
        private CompletableFuture<Void> preparation;
        private boolean finishedSending;

        private UploadSender(BlockPos anchor, XaeroWorldMapArchiveCompat.Snapshot snapshot) {
            this.id = snapshot.sessionId();
            this.anchor = anchor;
            this.snapshot = snapshot;
            this.totalParts = snapshot.regions().stream().mapToInt(region -> Math.toIntExact(Math.floorDiv(
                    region.descriptor().compressedSize() + XaeroArchiveLimits.PART_SIZE - 1,
                    XaeroArchiveLimits.PART_SIZE
            ))).sum();
            if (totalParts <= 0 || totalParts > XaeroArchiveLimits.MAX_PARTS) {
                throw new IllegalArgumentException("Invalid Xaero upload part count");
            }
            preparation = CompletableFuture.runAsync(this::prepare, IO_EXECUTOR);
        }

        private XaeroArchivePayloads.C2SBeginUpload beginPayload() {
            return new XaeroArchivePayloads.C2SBeginUpload(
                    id,
                    anchor,
                    snapshot.dimension(),
                    XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION,
                    snapshot.compressedSize(),
                    snapshot.uncompressedSize(),
                    totalParts,
                    snapshot.checksum(),
                    snapshot.regions().stream().map(XaeroWorldMapArchiveCompat.LocalRegion::descriptor).toList()
            );
        }

        private void send(int budget) throws IOException {
            if (preparationFailure != null) {
                throw preparationFailure;
            }
            for (int part = 0; part < budget; part++) {
                UploadPart next = prepared.poll();
                if (next == null) {
                    break;
                }
                PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SUploadPart(
                        id, next.sequence, totalParts, next.regionIndex, next.offset,
                        ArchiveHashing.sha256(next.data), next.data
                ));
                sequence++;
                sentBytes += next.data.length;
            }
            if (preparationComplete && prepared.isEmpty()) {
                if (sequence != totalParts || sentBytes != snapshot.compressedSize()) {
                    throw new IOException("Incomplete local Xaero snapshot");
                }
                finishedSending = true;
                PacketDistributor.sendToServer(new XaeroArchivePayloads.C2SFinishUpload(id, snapshot.checksum()));
            }
        }

        private void prepare() {
            int preparedSequence = 0;
            try {
                for (int preparedRegion = 0; preparedRegion < snapshot.regions().size() && !closed; preparedRegion++) {
                    XaeroWorldMapArchiveCompat.LocalRegion region = snapshot.regions().get(preparedRegion);
                    long offset = 0;
                    try (InputStream source = Files.newInputStream(region.path())) {
                        while (offset < region.descriptor().compressedSize() && !closed) {
                            int length = (int) Math.min(
                                    XaeroArchiveLimits.PART_SIZE,
                                    region.descriptor().compressedSize() - offset
                            );
                            byte[] data = source.readNBytes(length);
                            if (data.length != length) {
                                throw new IOException("Truncated local Xaero snapshot");
                            }
                            prepared.put(new UploadPart(preparedSequence++, preparedRegion, offset, data));
                            offset += data.length;
                        }
                    }
                }
                if (!closed && preparedSequence != totalParts) {
                    throw new IOException("Xaero upload preparation mismatch");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException exception) {
                preparationFailure = exception;
            } finally {
                preparationComplete = true;
            }
        }

        private void cleanup() {
            closed = true;
            prepared.clear();
            if (preparation != null) {
                preparation.cancel(true);
            }
            cleanupAsync(snapshot.root());
        }

        private record UploadPart(int sequence, int regionIndex, long offset, byte[] data) {
        }
    }

    private static final class DownloadReceiver {
        private final UUID id;
        private final ResourceLocation dimension;
        private final Path root;
        private final List<ArchiveRegionDescriptor> descriptors;
        private final long compressedSize;
        private final long uncompressedSize;
        private final int totalParts;
        private final int tileCount;
        private final String checksum;
        private final MessageDigest digest = ArchiveHashing.sha256();
        private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);
        private final AtomicInteger queuedParts = new AtomicInteger();
        private volatile boolean closed;
        private int expectedSequence;
        private int expectedRegion;
        private long expectedOffset;
        private long receivedBytes;

        private DownloadReceiver(XaeroArchivePayloads.S2CBeginDownload payload, Path root) {
            this.id = payload.sessionId();
            this.dimension = payload.dimension();
            this.root = root;
            this.descriptors = List.copyOf(payload.regions());
            this.compressedSize = payload.compressedSize();
            this.uncompressedSize = payload.uncompressedSize();
            this.totalParts = payload.totalParts();
            this.tileCount = descriptors.stream().mapToInt(ArchiveRegionDescriptor::tileCount).sum();
            this.checksum = payload.checksum();
        }

        private boolean accept(XaeroArchivePayloads.S2CDownloadPart part) throws IOException {
            if (part.sequence() != expectedSequence
                    || part.totalParts() != totalParts
                    || part.regionIndex() != expectedRegion
                    || expectedRegion >= descriptors.size()
                    || part.offset() != expectedOffset
                    || part.data().length == 0
                    || part.data().length > XaeroArchiveLimits.PART_SIZE
                    || !ArchiveHashing.sha256(part.data()).equals(part.checksum())) {
                throw new IOException("Unexpected Xaero download part");
            }
            ArchiveRegionDescriptor descriptor = descriptors.get(expectedRegion);
            int expectedLength = (int) Math.min(XaeroArchiveLimits.PART_SIZE, descriptor.compressedSize() - expectedOffset);
            if (part.data().length != expectedLength) {
                throw new IOException("Unexpected Xaero download part length");
            }
            if (queuedParts.get() >= 32) {
                throw new IOException("Xaero download write queue is full");
            }
            long offset = expectedOffset;
            int region = expectedRegion;
            byte[] data = part.data();
            queuedParts.incrementAndGet();
            writes = writes.thenRunAsync(() -> writePart(region, offset, data), IO_EXECUTOR)
                    .whenComplete((ignored, throwable) -> queuedParts.decrementAndGet());
            digest.update(part.data());
            expectedSequence++;
            expectedOffset += part.data().length;
            receivedBytes += part.data().length;
            if (expectedOffset == descriptor.compressedSize()) {
                expectedRegion++;
                expectedOffset = 0;
            }
            return expectedSequence == totalParts && expectedRegion == descriptors.size() && receivedBytes == compressedSize;
        }

        private void verify() throws IOException {
            if (!checksum.equals(ArchiveHashing.hex(digest.digest()))) {
                throw new IOException("Xaero download checksum mismatch");
            }
            for (ArchiveRegionDescriptor descriptor : descriptors) {
                Path path = path(descriptor.name());
                if (Files.size(path) != descriptor.compressedSize()
                        || !ArchiveHashing.sha256(path).equals(descriptor.checksum())) {
                    throw new IOException("Xaero download region checksum mismatch");
                }
                XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.inspect(path);
                if (stats.uncompressedSize() != descriptor.uncompressedSize() || stats.tileCount() != descriptor.tileCount()) {
                    throw new IOException("Xaero download region metadata mismatch");
                }
            }
        }

        private void finishAsync(
                Consumer<XaeroWorldMapArchiveCompat.Download> success,
                Consumer<Throwable> failure
        ) {
            writes.thenApplyAsync(ignored -> {
                try {
                    verify();
                    return asDownload();
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }, IO_EXECUTOR).whenComplete((result, throwable) -> Minecraft.getInstance().execute(() -> {
                if (throwable == null) {
                    success.accept(result);
                } else {
                    failure.accept(throwable);
                }
            }));
        }

        private void writePart(int regionIndex, long offset, byte[] data) {
            if (closed) {
                return;
            }
            try {
                Files.createDirectories(root);
                Path destination = path(descriptors.get(regionIndex).name());
                try (FileChannel channel = FileChannel.open(
                        destination,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                )) {
                    if (channel.size() != offset) {
                        throw new IOException("Xaero download staging size mismatch");
                    }
                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer, offset + buffer.position());
                    }
                }
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }

        private XaeroWorldMapArchiveCompat.Download asDownload() throws IOException {
            ArrayList<XaeroWorldMapArchiveCompat.LocalRegion> regions = new ArrayList<>(descriptors.size());
            for (ArchiveRegionDescriptor descriptor : descriptors) {
                regions.add(new XaeroWorldMapArchiveCompat.LocalRegion(descriptor, path(descriptor.name())));
            }
            return new XaeroWorldMapArchiveCompat.Download(
                    id, dimension, root, List.copyOf(regions), compressedSize, uncompressedSize, tileCount, checksum
            );
        }

        private Path path(String name) throws IOException {
            if (!ArchiveRegionDescriptor.isSafeName(name)) {
                throw new IOException("Unsafe Xaero download region name");
            }
            Path result = root.resolve(name).normalize();
            if (!result.startsWith(root)) {
                throw new IOException("Xaero download path traversal");
            }
            return result;
        }

        private void cleanup() {
            closed = true;
            writes.whenCompleteAsync((ignored, throwable) -> XaeroWorldMapArchiveCompat.cleanup(root), IO_EXECUTOR);
        }
    }

    private XaeroArchiveClient() {
    }
}
