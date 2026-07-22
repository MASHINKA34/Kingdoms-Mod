package com.geydev.kalfactions.integration.xaero.archive.client;

import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveRegionDescriptor;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveLimits;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveManifest;
import com.geydev.kalfactions.integration.xaero.archive.XaeroRegionCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class XaeroWorldMapArchiveCompat {
    public static Compatibility compatibility() {
        String worldMap = version("xaeroworldmap");
        String minimap = version("xaerominimap");
        if (!XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION.equals(worldMap)
                || !XaeroArchiveLimits.XAERO_MINIMAP_VERSION.equals(minimap)) {
            return new Compatibility(false, "kingdoms.xaero_archive.error.version");
        }
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable() || session.getMapProcessor() == null) {
                return new Compatibility(false, "kingdoms.xaero_archive.error.unavailable");
            }
            return new Compatibility(true, "");
        } catch (LinkageError | RuntimeException exception) {
            return new Compatibility(false, "kingdoms.xaero_archive.error.version");
        }
    }

    public static void snapshot(
            UUID sessionId,
            ResourceLocation dimension,
            Executor executor,
            Consumer<Snapshot> success,
            Consumer<Throwable> failure
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            MapProcessor processor = processor(dimension);
            processor.waitForLoadingToFinish(() -> {
                processor.pushWriterPause();
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return copySnapshot(sessionId, dimension, processor);
                    } catch (IOException exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                }, executor).whenComplete((snapshot, throwable) -> minecraft.execute(() -> {
                    processor.popWriterPause();
                    if (throwable == null) {
                        success.accept(snapshot);
                    } else {
                        failure.accept(unwrap(throwable));
                    }
                }));
            });
        } catch (Throwable throwable) {
            failure.accept(throwable);
        }
    }

    public static void inspectLocal(
            ResourceLocation dimension,
            Executor executor,
            Consumer<MapStats> success,
            Consumer<Throwable> failure
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            MapProcessor processor = processor(dimension);
            processor.waitForLoadingToFinish(() -> {
                processor.pushWriterPause();
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return inspectFolder(mapFolder(processor));
                    } catch (IOException exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                }, executor).whenComplete((stats, throwable) -> minecraft.execute(() -> {
                    processor.popWriterPause();
                    if (throwable == null) {
                        success.accept(stats);
                    } else {
                        failure.accept(unwrap(throwable));
                    }
                }));
            });
        } catch (Throwable throwable) {
            failure.accept(throwable);
        }
    }

    public static void importRegions(
            Download download,
            Executor executor,
            Runnable success,
            Consumer<Throwable> failure
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            MapProcessor processor = processor(download.dimension);
            processor.waitForLoadingToFinish(() -> {
                processor.pushWriterPause();
                CompletableFuture.runAsync(() -> {
                    try {
                        mergeIntoLocal(download, processor);
                    } catch (IOException exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                }, executor).whenComplete((ignored, throwable) -> minecraft.execute(() -> {
                    try {
                        if (throwable == null) {
                            try {
                                processor.getMapSaveLoad().detectRegions(20);
                                processor.getMapWorld().getCurrentDimension()
                                        .startFullMapReload(Integer.MAX_VALUE, false, processor);
                                success.run();
                            } catch (Throwable reloadFailure) {
                                failure.accept(reloadFailure);
                            }
                        } else {
                            failure.accept(unwrap(throwable));
                        }
                    } finally {
                        processor.popWriterPause();
                    }
                }));
            });
        } catch (Throwable throwable) {
            failure.accept(throwable);
        }
    }

    private static Snapshot copySnapshot(UUID sessionId, ResourceLocation dimension, MapProcessor processor) throws IOException {
        Path source = mapFolder(processor);
        Path root = transferRoot().resolve("upload-" + sessionId).normalize();
        ensureTransferPath(root);
        Files.createDirectories(root);
        try {
        ArrayList<Path> sources = new ArrayList<>();
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            try (var paths = Files.list(source)) {
                paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> ArchiveRegionDescriptor.isSafeName(path.getFileName().toString()))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .limit(XaeroArchiveLimits.MAX_REGIONS + 1L)
                        .forEach(sources::add);
            }
        }
        if (sources.size() > XaeroArchiveLimits.MAX_REGIONS) {
            throw new IOException("Too many local Xaero regions");
        }
        ArrayList<LocalRegion> regions = new ArrayList<>(sources.size());
        long compressed = 0;
        long uncompressed = 0;
        int tiles = 0;
        MessageDigest combined = ArchiveHashing.sha256();
        byte[] buffer = new byte[32 * 1024];
        for (Path local : sources) {
            String name = local.getFileName().toString();
            Path copy = root.resolve(name).normalize();
            if (!copy.startsWith(root)) {
                throw new IOException("Invalid local Xaero region path");
            }
            try (InputStream input = Files.newInputStream(local);
                 var output = Files.newOutputStream(copy, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        output.write(buffer, 0, read);
                        combined.update(buffer, 0, read);
                    }
                }
            }
            XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.inspect(copy);
            String checksum = ArchiveHashing.sha256(copy);
            ArchiveRegionDescriptor descriptor = new ArchiveRegionDescriptor(
                    name, stats.compressedSize(), stats.uncompressedSize(), stats.tileCount(), checksum
            );
            compressed = Math.addExact(compressed, stats.compressedSize());
            uncompressed = Math.addExact(uncompressed, stats.uncompressedSize());
            tiles = Math.addExact(tiles, stats.tileCount());
            if (compressed > XaeroArchiveLimits.MAX_SESSION_COMPRESSED_SIZE
                    || uncompressed > XaeroArchiveLimits.MAX_SESSION_UNCOMPRESSED_SIZE) {
                throw new IOException("Local Xaero map exceeds the archive limit");
            }
            regions.add(new LocalRegion(descriptor, copy));
        }
        if (regions.isEmpty()) {
            cleanup(root);
            throw new IOException("No local Xaero region data");
        }
        return new Snapshot(sessionId, dimension, root, List.copyOf(regions), compressed, uncompressed, tiles,
                ArchiveHashing.hex(combined.digest()));
        } catch (IOException | RuntimeException exception) {
            cleanup(root);
            throw exception;
        }
    }

    private static MapStats inspectFolder(Path folder) throws IOException {
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return new MapStats(0, 0, 0, 0);
        }
        ArrayList<Path> regions = new ArrayList<>();
        try (var paths = Files.list(folder)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> ArchiveRegionDescriptor.isSafeName(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(XaeroArchiveLimits.MAX_REGIONS + 1L)
                    .forEach(regions::add);
        }
        if (regions.size() > XaeroArchiveLimits.MAX_REGIONS) {
            throw new IOException("Too many local Xaero regions");
        }
        long compressed = 0;
        long uncompressed = 0;
        int tiles = 0;
        for (Path region : regions) {
            XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.inspect(region);
            compressed = Math.addExact(compressed, stats.compressedSize());
            uncompressed = Math.addExact(uncompressed, stats.uncompressedSize());
            tiles = Math.addExact(tiles, stats.tileCount());
            if (compressed > XaeroArchiveLimits.MAX_SESSION_COMPRESSED_SIZE
                    || uncompressed > XaeroArchiveLimits.MAX_SESSION_UNCOMPRESSED_SIZE) {
                throw new IOException("Local Xaero map exceeds the archive limit");
            }
        }
        return new MapStats(compressed, uncompressed, regions.size(), tiles);
    }

    private static void mergeIntoLocal(Download download, MapProcessor processor) throws IOException {
        Path localFolder = mapFolder(processor);
        Files.createDirectories(localFolder);
        Path prepared = download.root.resolve("prepared").normalize();
        ensureTransferPath(prepared);
        Files.createDirectories(prepared);
        for (LocalRegion incoming : download.regions) {
            Path local = localFolder.resolve(incoming.descriptor.name()).normalize();
            if (!local.startsWith(localFolder) || Files.isSymbolicLink(local)) {
                throw new IOException("Unsafe local Xaero region path");
            }
            Path merged = prepared.resolve(incoming.descriptor.name()).normalize();
            XaeroRegionCodec.merge(Files.isRegularFile(local, LinkOption.NOFOLLOW_LINKS) ? local : null, incoming.path, merged);
        }
        for (LocalRegion incoming : download.regions) {
            Path merged = prepared.resolve(incoming.descriptor.name()).normalize();
            Path local = localFolder.resolve(incoming.descriptor.name()).normalize();
            XaeroArchiveManifest.moveAtomic(merged, local);
        }
    }

    private static MapProcessor processor(ResourceLocation dimension) throws IOException {
        Compatibility compatibility = compatibility();
        if (!compatibility.compatible) {
            throw new IOException(compatibility.errorKey);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(dimension)) {
            throw new IOException("Xaero archive dimension changed");
        }
        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session.getMapProcessor();
        if (processor.getMapWorld() == null
                || processor.getMapWorld().getCurrentDimensionId() == null
                || !processor.getMapWorld().getCurrentDimensionId().location().equals(dimension)
                || processor.getCurrentWorldId() == null
                || processor.getCurrentDimId() == null) {
            throw new IOException("Xaero map world is not ready");
        }
        return processor;
    }

    private static Path mapFolder(MapProcessor processor) throws IOException {
        Path root = processor.getMapSaveLoad().getRootFolder(processor.getCurrentWorldId()).toAbsolutePath().normalize();
        Path folder = processor.getMapSaveLoad().getMWSubFolder(
                processor.getCurrentWorldId(), processor.getCurrentDimId(), processor.getCurrentMWId()
        ).toAbsolutePath().normalize();
        if (!folder.startsWith(root)) {
            throw new IOException("Xaero map folder escaped its root");
        }
        return folder;
    }

    private static Path transferRoot() {
        return Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize()
                .resolve("kingdoms").resolve("xaero_transfers").normalize();
    }

    private static void ensureTransferPath(Path path) throws IOException {
        if (!path.toAbsolutePath().normalize().startsWith(transferRoot())) {
            throw new IOException("Invalid Xaero transfer path");
        }
    }

    public static void cleanup(Path root) {
        if (root == null || !root.toAbsolutePath().normalize().startsWith(transferRoot()) || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static String version(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record Compatibility(boolean compatible, String errorKey) {
    }

    public record LocalRegion(ArchiveRegionDescriptor descriptor, Path path) {
    }

    public record MapStats(long compressedSize, long uncompressedSize, int regionCount, int tileCount) {
    }

    public record Snapshot(
            UUID sessionId,
            ResourceLocation dimension,
            Path root,
            List<LocalRegion> regions,
            long compressedSize,
            long uncompressedSize,
            int tileCount,
            String checksum
    ) {
    }

    public record Download(
            UUID sessionId,
            ResourceLocation dimension,
            Path root,
            List<LocalRegion> regions,
            long compressedSize,
            long uncompressedSize,
            int tileCount,
            String checksum
    ) {
    }

    private XaeroWorldMapArchiveCompat() {
    }
}
