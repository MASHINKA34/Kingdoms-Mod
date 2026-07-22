package com.geydev.kalfactions.integration.xaero.archive;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import com.geydev.kalfactions.dimension.DimensionControlManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class XaeroArchiveStore {
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private static final Map<Path, Integer> ACTIVE_BLOBS = new ConcurrentHashMap<>();

    public static ArchiveLocation location(MinecraftServer server, UUID factionId, ResourceLocation dimension) throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String serverIdentity = serverIdentity(server, worldRoot);
        String dimensionScope = dimension.toString();
        if (Level.NETHER.location().equals(dimension)) {
            dimensionScope += "#wipe=" + DimensionControlManager.get(server).wipeGeneration(Level.NETHER);
        }
        String dimensionName = ArchiveHashing.sha256(dimensionScope.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
        Path root = worldRoot.resolve("kingdoms").resolve("xaero_maps").resolve(serverIdentity)
                .resolve(factionId.toString()).resolve(dimensionName).normalize();
        Path expected = worldRoot.resolve("kingdoms").resolve("xaero_maps").normalize();
        if (!root.startsWith(expected)) {
            throw new IOException("Invalid Xaero archive path");
        }
        return new ArchiveLocation(root, serverIdentity, factionId, dimension.toString());
    }

    public static XaeroArchiveManifest merge(ArchiveLocation location, List<IncomingRegion> incoming) throws IOException {
        Object lock = LOCKS.computeIfAbsent(location.root, ignored -> new Object());
        synchronized (lock) {
            Files.createDirectories(location.root);
            Path blobs = location.root.resolve("blobs");
            Path staging = location.root.resolve("staging");
            Files.createDirectories(blobs);
            Files.createDirectories(staging);
            XaeroArchiveManifest current = load(location);
            Map<String, ArchiveRegionDescriptor> entries = new HashMap<>();
            current.regions().forEach(descriptor -> entries.put(descriptor.name(), descriptor));
            ArrayList<Path> temporaryFiles = new ArrayList<>();
            try {
                for (IncomingRegion region : incoming.stream().sorted(Comparator.comparing(IncomingRegion::name)).toList()) {
                    if (!ArchiveRegionDescriptor.isSafeName(region.name)) {
                        throw new IOException("Unsafe Xaero region name");
                    }
                    ArchiveRegionDescriptor previous = entries.get(region.name);
                    Path previousBlob = previous == null ? null : checkedBlob(blobs, previous.checksum());
                    if (previousBlob != null && !Files.isRegularFile(previousBlob)) {
                        throw new IOException("Xaero archive blob is missing");
                    }
                    Path merged = staging.resolve(UUID.randomUUID() + ".zip");
                    temporaryFiles.add(merged);
                    XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.merge(previousBlob, region.path, merged);
                    String checksum = ArchiveHashing.sha256(merged);
                    Path blob = checkedBlob(blobs, checksum);
                    if (!Files.exists(blob)) {
                        try {
                            Files.move(merged, blob, StandardCopyOption.ATOMIC_MOVE);
                        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                            Files.move(merged, blob);
                        } catch (java.nio.file.FileAlreadyExistsException ignored) {
                            Files.deleteIfExists(merged);
                        }
                    }
                    entries.put(region.name, new ArchiveRegionDescriptor(
                            region.name,
                            stats.compressedSize(),
                            stats.uncompressedSize(),
                            stats.tileCount(),
                            checksum
                    ));
                }
                ArrayList<ArchiveRegionDescriptor> sorted = new ArrayList<>(entries.values());
                sorted.sort(Comparator.comparing(ArchiveRegionDescriptor::name));
                validateAggregate(sorted);
                XaeroArchiveManifest updated = new XaeroArchiveManifest(
                        XaeroArchiveLimits.FORMAT_VERSION,
                        XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION,
                        location.serverIdentity,
                        location.factionId,
                        location.dimension,
                        Instant.now().toEpochMilli(),
                        sorted
                );
                updated.writeAtomic(location.root.resolve("manifest.json"));
                collectOrphanBlobs(location, updated);
                return updated;
            } catch (IOException | RuntimeException exception) {
                collectOrphanBlobs(location, current);
                throw exception;
            } finally {
                for (Path temporary : temporaryFiles) {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }

    public static Snapshot snapshot(ArchiveLocation location) throws IOException {
        Object lock = LOCKS.computeIfAbsent(location.root, ignored -> new Object());
        synchronized (lock) {
            XaeroArchiveManifest manifest = load(location);
            ArrayList<SnapshotRegion> regions = new ArrayList<>(manifest.regions().size());
            for (ArchiveRegionDescriptor descriptor : manifest.regions()) {
                Path blob = checkedBlob(location.root.resolve("blobs"), descriptor.checksum());
                if (!Files.isRegularFile(blob)
                        || Files.size(blob) != descriptor.compressedSize()
                        || !ArchiveHashing.sha256(blob).equals(descriptor.checksum())) {
                    throw new IOException("Xaero archive blob failed integrity verification");
                }
                regions.add(new SnapshotRegion(descriptor, blob));
            }
            Set<Path> leased = regions.stream().map(SnapshotRegion::path).collect(java.util.stream.Collectors.toUnmodifiableSet());
            leased.forEach(path -> ACTIVE_BLOBS.merge(path, 1, Integer::sum));
            return new Snapshot(location, manifest, List.copyOf(regions), leased);
        }
    }

    public static XaeroArchiveManifest load(ArchiveLocation location) throws IOException {
        XaeroArchiveManifest manifest = XaeroArchiveManifest.read(
                location.root.resolve("manifest.json"),
                location.serverIdentity,
                location.factionId,
                location.dimension
        );
        validateAggregate(manifest.regions());
        return manifest;
    }

    private static Path checkedBlob(Path blobs, String checksum) throws IOException {
        if (!ArchiveHashing.isSha256(checksum)) {
            throw new IOException("Invalid Xaero blob checksum");
        }
        Path normalizedBlobs = blobs.toAbsolutePath().normalize();
        Path blob = normalizedBlobs.resolve(checksum + ".zip").normalize();
        if (!blob.startsWith(normalizedBlobs)) {
            throw new IOException("Invalid Xaero blob path");
        }
        return blob;
    }

    private static void validateAggregate(List<ArchiveRegionDescriptor> regions) throws IOException {
        if (regions.size() > XaeroArchiveLimits.MAX_REGIONS) {
            throw new IOException("Xaero archive region count exceeds the limit");
        }
        long compressed = 0;
        long uncompressed = 0;
        try {
            for (ArchiveRegionDescriptor descriptor : regions) {
                compressed = Math.addExact(compressed, descriptor.compressedSize());
                uncompressed = Math.addExact(uncompressed, descriptor.uncompressedSize());
            }
        } catch (ArithmeticException exception) {
            throw new IOException("Xaero archive aggregate size overflow", exception);
        }
        if (compressed > XaeroArchiveLimits.MAX_SESSION_COMPRESSED_SIZE
                || uncompressed > XaeroArchiveLimits.MAX_SESSION_UNCOMPRESSED_SIZE) {
            throw new IOException("Xaero archive aggregate size exceeds the limit");
        }
    }

    private static void collectOrphanBlobs(ArchiveLocation location, XaeroArchiveManifest manifest) {
        Path blobs = location.root.resolve("blobs").toAbsolutePath().normalize();
        if (!Files.isDirectory(blobs)) {
            return;
        }
        Set<Path> referenced = manifest.regions().stream().map(descriptor -> {
            try {
                return checkedBlob(blobs, descriptor.checksum());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).collect(java.util.stream.Collectors.toUnmodifiableSet());
        try (var paths = Files.list(blobs)) {
            paths.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}\\.zip"))
                    .filter(path -> !referenced.contains(path.toAbsolutePath().normalize()))
                    .filter(path -> !ACTIVE_BLOBS.containsKey(path.toAbsolutePath().normalize()))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            KalFactions.LOGGER.warn("Could not remove orphaned Xaero archive blob {}", path, exception);
                        }
                    });
        } catch (IOException | IllegalStateException exception) {
            KalFactions.LOGGER.warn("Could not collect orphaned Xaero archive blobs under {}", blobs, exception);
        }
    }

    private static void releaseSnapshot(Snapshot snapshot) {
        Object lock = LOCKS.computeIfAbsent(snapshot.location.root, ignored -> new Object());
        synchronized (lock) {
            for (Path path : snapshot.leased) {
                ACTIVE_BLOBS.computeIfPresent(path, (ignored, count) -> count <= 1 ? null : count - 1);
            }
            try {
                collectOrphanBlobs(snapshot.location, load(snapshot.location));
            } catch (IOException exception) {
                KalFactions.LOGGER.warn("Could not collect Xaero archive blobs after download", exception);
            }
        }
    }

    private static String serverIdentity(MinecraftServer server, Path worldRoot) {
        String identitySource = worldRoot + "\n" + server.overworld().getSeed();
        return ArchiveHashing.sha256(identitySource.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    public record ArchiveLocation(Path root, String serverIdentity, UUID factionId, String dimension) {
    }

    public record IncomingRegion(String name, Path path) {
    }

    public static final class Snapshot implements AutoCloseable {
        private final ArchiveLocation location;
        private final XaeroArchiveManifest manifest;
        private final List<SnapshotRegion> regions;
        private final Set<Path> leased;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Snapshot(
                ArchiveLocation location,
                XaeroArchiveManifest manifest,
                List<SnapshotRegion> regions,
                Set<Path> leased
        ) {
            this.location = location;
            this.manifest = manifest;
            this.regions = regions;
            this.leased = leased;
        }

        public XaeroArchiveManifest manifest() {
            return manifest;
        }

        public List<SnapshotRegion> regions() {
            return regions;
        }

        public long compressedSize() {
            return regions.stream().mapToLong(region -> region.descriptor.compressedSize()).sum();
        }

        public long uncompressedSize() {
            return regions.stream().mapToLong(region -> region.descriptor.uncompressedSize()).sum();
        }

        public int tileCount() {
            return regions.stream().mapToInt(region -> region.descriptor.tileCount()).sum();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                releaseSnapshot(this);
            }
        }
    }

    public record SnapshotRegion(ArchiveRegionDescriptor descriptor, Path path) {
    }

    private XaeroArchiveStore() {
    }
}
