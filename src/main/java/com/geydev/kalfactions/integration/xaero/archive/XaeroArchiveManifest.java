package com.geydev.kalfactions.integration.xaero.archive;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record XaeroArchiveManifest(
        int formatVersion,
        String xaeroWorldMapVersion,
        String serverIdentity,
        UUID factionId,
        String dimension,
        long updatedAt,
        List<ArchiveRegionDescriptor> regions
) {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public XaeroArchiveManifest {
        regions = List.copyOf(regions);
    }

    public static XaeroArchiveManifest empty(String serverIdentity, UUID factionId, String dimension) {
        return new XaeroArchiveManifest(
                XaeroArchiveLimits.FORMAT_VERSION,
                XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION,
                serverIdentity,
                factionId,
                dimension,
                Instant.now().toEpochMilli(),
                List.of()
        );
    }

    public static XaeroArchiveManifest read(Path path, String serverIdentity, UUID factionId, String dimension) throws IOException {
        if (!Files.exists(path)) {
            return empty(serverIdentity, factionId, dimension);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            XaeroArchiveManifest manifest = GSON.fromJson(reader, XaeroArchiveManifest.class);
            if (manifest == null
                    || manifest.formatVersion != XaeroArchiveLimits.FORMAT_VERSION
                    || !XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION.equals(manifest.xaeroWorldMapVersion)
                    || !serverIdentity.equals(manifest.serverIdentity)
                    || !factionId.equals(manifest.factionId)
                    || !dimension.equals(manifest.dimension)
                    || manifest.regions == null
                    || manifest.regions.size() > XaeroArchiveLimits.MAX_REGIONS) {
                throw new IOException("Xaero archive manifest identity or version mismatch");
            }
            ArrayList<ArchiveRegionDescriptor> validated = new ArrayList<>(manifest.regions.size());
            for (ArchiveRegionDescriptor descriptor : manifest.regions) {
                validated.add(new ArchiveRegionDescriptor(
                        descriptor.name(),
                        descriptor.compressedSize(),
                        descriptor.uncompressedSize(),
                        descriptor.tileCount(),
                        descriptor.checksum()
                ));
            }
            validated.sort(Comparator.comparing(ArchiveRegionDescriptor::name));
            for (int index = 1; index < validated.size(); index++) {
                if (validated.get(index - 1).name().equals(validated.get(index).name())) {
                    throw new IOException("Duplicate Xaero archive region");
                }
            }
            return new XaeroArchiveManifest(
                    manifest.formatVersion,
                    manifest.xaeroWorldMapVersion,
                    manifest.serverIdentity,
                    manifest.factionId,
                    manifest.dimension,
                    manifest.updatedAt,
                    validated
            );
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid Xaero archive manifest", exception);
        }
    }

    public void writeAtomic(Path path) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Xaero manifest has no parent");
        }
        Files.createDirectories(parent);
        Path temporary = parent.resolve("manifest.json.tmp-" + UUID.randomUUID());
        try {
            try (Writer writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                GSON.toJson(this, writer);
            }
            moveAtomic(temporary, path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void moveAtomic(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
