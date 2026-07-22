package com.geydev.kalfactions.integration.xaero.archive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class XaeroRegionCodecTest {
    @TempDir
    Path temporary;

    @Test
    void unionsNonOverlappingTilesInsideTheSameRegion() throws IOException {
        Path first = region("first.zip", Set.of(0), 10);
        Path second = region("second.zip", Set.of(1), 20);
        Path merged = temporary.resolve("merged.zip");

        XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.merge(first, second, merged);

        assertEquals(2, stats.tileCount());
        assertTrue(stats.compressedSize() > 0);
        assertTrue(stats.uncompressedSize() > 0);
    }

    @Test
    void incomingTileWinsCollisionDeterministically() throws IOException {
        Path first = region("first.zip", Set.of(0), 10);
        Path second = region("second.zip", Set.of(0), 20);
        Path expected = temporary.resolve("expected.zip");
        Path merged = temporary.resolve("merged.zip");

        XaeroRegionCodec.merge(null, second, expected);
        XaeroRegionCodec.merge(first, second, merged);

        assertEquals(1, XaeroRegionCodec.inspect(merged).tileCount());
        assertEquals(ArchiveHashing.sha256(expected), ArchiveHashing.sha256(merged));
    }

    @Test
    void emptyIncomingRegionCannotEraseExistingTiles() throws IOException {
        Path first = region("first.zip", Set.of(0, 7), 10);
        Path empty = region("empty.zip", Set.of(), 0);
        Path normalized = temporary.resolve("normalized.zip");
        Path merged = temporary.resolve("merged.zip");

        XaeroRegionCodec.merge(null, first, normalized);
        XaeroRegionCodec.merge(first, empty, merged);

        assertEquals(2, XaeroRegionCodec.inspect(merged).tileCount());
        assertEquals(ArchiveHashing.sha256(normalized), ArchiveHashing.sha256(merged));
    }

    @Test
    void realXaeroRegionRoundTripsWithoutLosingTiles() throws IOException {
        Path runRoot = Path.of("run", "xaero", "world-map");
        assumeTrue(Files.isDirectory(runRoot));
        Path source;
        try (var paths = Files.walk(runRoot)) {
            source = paths.filter(Files::isRegularFile)
                    .filter(path -> ArchiveRegionDescriptor.isSafeName(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst()
                    .orElse(null);
        }
        assumeTrue(source != null);
        XaeroRegionCodec.RegionStats original = XaeroRegionCodec.inspect(source);
        Path once = temporary.resolve("once.zip");
        Path twice = temporary.resolve("twice.zip");

        XaeroRegionCodec.merge(null, source, once);
        XaeroRegionCodec.merge(once, source, twice);

        assertEquals(original.tileCount(), XaeroRegionCodec.inspect(once).tileCount());
        assertEquals(ArchiveHashing.sha256(once), ArchiveHashing.sha256(twice));
    }

    @Test
    void rejectsTraversalNamesBeforeTheyReachStorage() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArchiveRegionDescriptor("../0_0.zip", 1, 1, 1, "0".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new ArchiveRegionDescriptor("0_0.zip/extra", 1, 1, 1, "0".repeat(64)));
    }

    @Test
    void manifestRoundTripsWithIdentityValidation() throws IOException {
        UUID factionId = UUID.randomUUID();
        ArchiveRegionDescriptor descriptor = new ArchiveRegionDescriptor("0_0.zip", 10, 100, 2, "a".repeat(64));
        XaeroArchiveManifest manifest = new XaeroArchiveManifest(
                XaeroArchiveLimits.FORMAT_VERSION,
                XaeroArchiveLimits.XAERO_WORLD_MAP_VERSION,
                "server-id",
                factionId,
                "minecraft:overworld",
                1,
                List.of(descriptor)
        );
        Path path = temporary.resolve("manifest.json");

        manifest.writeAtomic(path);

        assertEquals(manifest, XaeroArchiveManifest.read(path, "server-id", factionId, "minecraft:overworld"));
        assertThrows(IOException.class,
                () -> XaeroArchiveManifest.read(path, "other-server", factionId, "minecraft:overworld"));
    }

    private Path region(String name, Set<Integer> tiles, int height) throws IOException {
        Path path = temporary.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            ZipEntry entry = new ZipEntry("region.xaero");
            entry.setTime(0L);
            zip.putNextEntry(entry);
            DataOutputStream output = new DataOutputStream(zip);
            output.writeByte(255);
            output.writeInt(0x00060008);
            if (!tiles.isEmpty()) {
                output.writeByte(0);
                int parameters = (height & 0xFF) << 12 | (height >> 8 & 15) << 25;
                for (int tile = 0; tile < 16; tile++) {
                    if (!tiles.contains(tile)) {
                        output.writeInt(-1);
                        continue;
                    }
                    for (int pixel = 0; pixel < 256; pixel++) {
                        output.writeInt(parameters);
                    }
                    output.writeByte(0);
                    output.writeInt(0);
                    output.writeByte(32);
                }
            }
            output.flush();
            zip.closeEntry();
        }
        return path;
    }
}
