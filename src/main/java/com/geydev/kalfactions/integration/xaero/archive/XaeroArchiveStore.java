package com.geydev.kalfactions.integration.xaero.archive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class XaeroArchiveStore {
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

    public static ArchiveLocation location(MinecraftServer server, UUID factionId, ResourceLocation dimension) throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String serverIdentity = serverIdentity(server, worldRoot);
        String dimensionName = ArchiveHashing.sha256(dimension.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 32);
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
                return updated;
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
            return new Snapshot(manifest, List.copyOf(regions));
        }
    }

    public static XaeroArchiveManifest load(ArchiveLocation location) throws IOException {
        return XaeroArchiveManifest.read(
                location.root.resolve("manifest.json"),
                location.serverIdentity,
                location.factionId,
                location.dimension
        );
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

    private static String serverIdentity(MinecraftServer server, Path worldRoot) {
        String identitySource = worldRoot + "\n" + server.overworld().getSeed();
        return ArchiveHashing.sha256(identitySource.getBytes(StandardCharsets.UTF_8)).substring(0, 32);
    }

    public record ArchiveLocation(Path root, String serverIdentity, UUID factionId, String dimension) {
    }

    public record IncomingRegion(String name, Path path) {
    }

    public record Snapshot(XaeroArchiveManifest manifest, List<SnapshotRegion> regions) {
        public long compressedSize() {
            return regions.stream().mapToLong(region -> region.descriptor.compressedSize()).sum();
        }

        public long uncompressedSize() {
            return regions.stream().mapToLong(region -> region.descriptor.uncompressedSize()).sum();
        }

        public int tileCount() {
            return regions.stream().mapToInt(region -> region.descriptor.tileCount()).sum();
        }
    }

    public record SnapshotRegion(ArchiveRegionDescriptor descriptor, Path path) {
    }

    private XaeroArchiveStore() {
    }
}
