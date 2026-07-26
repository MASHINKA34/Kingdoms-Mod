package com.geydev.kalfactions.integration.xaero.archive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XaeroRegionCodecRoundTripTest {
    private static final int FULL_VERSION = 0x00060008;
    private static final int STATE_PRESENT = 1;
    private static final int OVERLAYS_PRESENT = 2;
    private static final int BIOME_PRESENT = 0x00100000;
    private static final int STATE_PALETTE_NEW = 0x00200000;
    private static final int BIOME_PALETTE_NEW = 0x00400000;
    private static final int TOP_HEIGHT_PRESENT = 0x01000000;
    private static final int OVERLAY_PALETTE_NEW = 0x00000400;

    @Test
    void reencodesXaeroRegionByteForByte() throws IOException {
        Path directory = Files.createTempDirectory("kingdoms-xaero-codec");
        Path source = directory.resolve("source.zip");
        Path destination = directory.resolve("destination.zip");
        byte[] original = region();
        writeArchive(source, original);

        XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.merge(null, source, destination);

        assertEquals(31, stats.tileCount());
        assertEquals(original.length, stats.uncompressedSize());
        assertArrayEquals(original, payload(destination));
    }

    @Test
    void mergeKeepsTilesFromBothRegions() throws IOException {
        Path directory = Files.createTempDirectory("kingdoms-xaero-merge");
        Path existing = directory.resolve("existing.zip");
        Path incoming = directory.resolve("incoming.zip");
        Path merged = directory.resolve("merged.zip");
        writeArchive(existing, singleChunkRegion(0, 0));
        writeArchive(incoming, singleChunkRegion(3, 5));

        XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.merge(existing, incoming, merged);

        assertEquals(32, stats.tileCount());
    }

    private static byte[] region() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(buffer);
        output.writeByte(255);
        output.writeInt(FULL_VERSION);
        Palette palette = new Palette();
        writeChunk(output, 0, 0, palette, 16);
        writeChunk(output, 7, 3, palette, 15);
        output.flush();
        return buffer.toByteArray();
    }

    private static byte[] singleChunkRegion(int chunkX, int chunkZ) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(buffer);
        output.writeByte(255);
        output.writeInt(FULL_VERSION);
        writeChunk(output, chunkX, chunkZ, new Palette(), 16);
        output.flush();
        return buffer.toByteArray();
    }

    private static void writeChunk(DataOutputStream output, int chunkX, int chunkZ, Palette palette, int tiles)
            throws IOException {
        output.writeByte(chunkX << 4 | chunkZ);
        for (int tile = 0; tile < 16; tile++) {
            if (tile >= tiles) {
                output.writeInt(-1);
                continue;
            }
            for (int pixel = 0; pixel < 256; pixel++) {
                writePixel(output, palette, pixel);
            }
            output.writeByte(4);
            output.writeInt(-64 + tile);
            output.writeByte(8);
        }
    }

    private static void writePixel(DataOutputStream output, Palette palette, int pixel) throws IOException {
        boolean overlay = pixel % 64 == 5;
        int parameters = STATE_PRESENT | BIOME_PRESENT | TOP_HEIGHT_PRESENT;
        if (overlay) {
            parameters |= OVERLAYS_PRESENT;
        }
        boolean newState = !palette.stateWritten;
        boolean newBiome = !palette.biomeWritten;
        if (newState) {
            parameters |= STATE_PALETTE_NEW;
        }
        if (newBiome) {
            parameters |= BIOME_PALETTE_NEW;
        }
        output.writeInt(parameters);
        if (newState) {
            NbtIo.write(state("minecraft:stone"), output);
            palette.stateWritten = true;
            palette.stateIndex = palette.size++;
        } else {
            output.writeInt(palette.stateIndex);
        }
        output.writeByte(72);
        if (overlay) {
            output.writeByte(1);
            boolean newOverlay = !palette.overlayWritten;
            output.writeInt(STATE_PRESENT | (newOverlay ? OVERLAY_PALETTE_NEW : 0));
            if (newOverlay) {
                NbtIo.write(state("minecraft:water"), output);
                palette.overlayWritten = true;
                palette.overlayIndex = palette.size++;
            } else {
                output.writeInt(palette.overlayIndex);
            }
        }
        if (newBiome) {
            output.writeUTF("minecraft:plains");
            palette.biomeWritten = true;
        } else {
            output.writeInt(0);
        }
    }

    private static CompoundTag state(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    private static void writeArchive(Path archive, byte[] content) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            ZipEntry entry = new ZipEntry("region.xaero");
            entry.setTime(0L);
            zip.putNextEntry(entry);
            zip.write(content);
            zip.closeEntry();
        }
    }

    private static byte[] payload(Path archive) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            assertNotNull(entry);
            assertEquals("region.xaero", entry.getName());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            zip.transferTo(output);
            return output.toByteArray();
        }
    }

    private static final class Palette {
        private boolean stateWritten;
        private boolean biomeWritten;
        private boolean overlayWritten;
        private int stateIndex;
        private int overlayIndex;
        private int size;
    }
}
