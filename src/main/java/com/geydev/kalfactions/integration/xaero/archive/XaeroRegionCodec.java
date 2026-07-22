package com.geydev.kalfactions.integration.xaero.archive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class XaeroRegionCodec {
    private static final int FULL_VERSION = 0x00060008;
    private static final int STATE_PRESENT = 1;
    private static final int OVERLAYS_PRESENT = 2;
    private static final int BIOME_PRESENT = 0x00100000;
    private static final int STATE_PALETTE_NEW = 0x00200000;
    private static final int BIOME_PALETTE_NEW = 0x00400000;
    private static final int BIOME_AS_INT = 0x00800000;
    private static final int TOP_HEIGHT_PRESENT = 0x01000000;
    private static final int OVERLAY_PALETTE_NEW = 0x00000400;
    private static final int MAX_NBT_BYTES = 1024 * 1024;
    private static final int MAX_PALETTE_SIZE = 65_536;

    public static RegionStats inspect(Path source) throws IOException {
        RegionData data = read(source);
        return new RegionStats(Files.size(source), data.uncompressedSize, data.tiles.size());
    }

    public static RegionStats merge(Path existing, Path incoming, Path destination) throws IOException {
        RegionData incomingData = read(incoming);
        RegionData merged = existing == null || !Files.exists(existing) ? incomingData : read(existing);
        if (merged != incomingData) {
            merged.tiles.putAll(incomingData.tiles);
        }
        write(merged, destination);
        return inspect(destination);
    }

    private static RegionData read(Path source) throws IOException {
        long compressedSize = Files.size(source);
        if (compressedSize <= 0 || compressedSize > XaeroArchiveLimits.MAX_REGION_COMPRESSED_SIZE) {
            throw new IOException("Xaero region compressed size exceeds the limit");
        }
        try (InputStream fileInput = Files.newInputStream(source);
             ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(fileInput, 32 * 1024))) {
            ZipEntry entry = zipInput.getNextEntry();
            if (entry == null || entry.isDirectory() || !"region.xaero".equals(entry.getName())) {
                throw new IOException("Xaero region ZIP must contain region.xaero");
            }
            BoundedInputStream bounded = new BoundedInputStream(zipInput, XaeroArchiveLimits.MAX_REGION_UNCOMPRESSED_SIZE);
            DataInputStream input = new DataInputStream(bounded);
            if (input.readUnsignedByte() != 255 || input.readInt() != FULL_VERSION) {
                throw new IOException("Unsupported Xaero region format; expected 6.8");
            }
            List<CompoundTag> statePalette = new ArrayList<>();
            List<String> biomePalette = new ArrayList<>();
            Map<Integer, TileData> tiles = new LinkedHashMap<>();
            boolean[] chunks = new boolean[64];
            int chunkCoordinate;
            while ((chunkCoordinate = input.read()) >= 0) {
                int chunkX = chunkCoordinate >>> 4;
                int chunkZ = chunkCoordinate & 15;
                if (chunkX > 7 || chunkZ > 7) {
                    throw new IOException("Invalid Xaero map-chunk coordinate");
                }
                int chunkIndex = chunkX * 8 + chunkZ;
                if (chunks[chunkIndex]) {
                    throw new IOException("Duplicate Xaero map-chunk coordinate");
                }
                chunks[chunkIndex] = true;
                for (int tileX = 0; tileX < 4; tileX++) {
                    for (int tileZ = 0; tileZ < 4; tileZ++) {
                        int firstParameters = input.readInt();
                        if (firstParameters == -1) {
                            continue;
                        }
                        List<PixelData> pixels = new ArrayList<>(256);
                        for (int pixel = 0; pixel < 256; pixel++) {
                            int parameters = pixel == 0 ? firstParameters : input.readInt();
                            pixels.add(readPixel(input, parameters, statePalette, biomePalette));
                        }
                        byte interpretationVersion = input.readByte();
                        int caveStart = input.readInt();
                        byte caveDepth = input.readByte();
                        int tileIndex = chunkIndex * 16 + tileX * 4 + tileZ;
                        if (tiles.put(tileIndex, new TileData(pixels, interpretationVersion, caveStart, caveDepth)) != null) {
                            throw new IOException("Duplicate Xaero tile");
                        }
                    }
                }
            }
            zipInput.closeEntry();
            if (zipInput.getNextEntry() != null) {
                throw new IOException("Unexpected extra entry in Xaero region ZIP");
            }
            return new RegionData(tiles, bounded.count());
        } catch (EOFException exception) {
            throw new IOException("Truncated Xaero region", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Xaero region data", exception);
        }
    }

    private static PixelData readPixel(
            DataInputStream input,
            int parameters,
            List<CompoundTag> statePalette,
            List<String> biomePalette
    ) throws IOException {
        CompoundTag state = null;
        if ((parameters & STATE_PRESENT) != 0) {
            state = readState(input, parameters, STATE_PALETTE_NEW, statePalette);
        }
        Byte topHeight = (parameters & TOP_HEIGHT_PRESENT) != 0 ? input.readByte() : null;
        List<OverlayData> overlays = List.of();
        if ((parameters & OVERLAYS_PRESENT) != 0) {
            int count = input.readUnsignedByte();
            if (count == 0 || count > 64) {
                throw new IOException("Invalid Xaero overlay count");
            }
            ArrayList<OverlayData> mutable = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int overlayParameters = input.readInt();
                CompoundTag overlayState = null;
                if ((overlayParameters & STATE_PRESENT) != 0) {
                    overlayState = readState(input, overlayParameters, OVERLAY_PALETTE_NEW, statePalette);
                }
                mutable.add(new OverlayData(overlayParameters, overlayState));
            }
            overlays = List.copyOf(mutable);
        }
        String biome = null;
        if ((parameters & BIOME_PRESENT) != 0) {
            if ((parameters & BIOME_PALETTE_NEW) != 0) {
                if ((parameters & BIOME_AS_INT) != 0) {
                    throw new IOException("Legacy integer biome palette is not valid for Xaero 6.8");
                }
                biome = input.readUTF();
                if (biome.length() > 256) {
                    throw new IOException("Xaero biome identifier is too long");
                }
                if (biomePalette.size() >= MAX_PALETTE_SIZE) {
                    throw new IOException("Xaero biome palette exceeds the limit");
                }
                biomePalette.add(biome);
            } else {
                int paletteIndex = input.readInt();
                biome = paletteValue(biomePalette, paletteIndex, "biome");
            }
        }
        return new PixelData(parameters, state, topHeight, overlays, biome);
    }

    private static CompoundTag readState(
            DataInputStream input,
            int parameters,
            int newFlag,
            List<CompoundTag> palette
    ) throws IOException {
        if ((parameters & newFlag) != 0) {
            if (palette.size() >= MAX_PALETTE_SIZE) {
                throw new IOException("Xaero block-state palette exceeds the limit");
            }
            CompoundTag state = NbtIo.read(input, NbtAccounter.create(MAX_NBT_BYTES));
            palette.add(state);
            return state;
        }
        return paletteValue(palette, input.readInt(), "block state");
    }

    private static <T> T paletteValue(List<T> palette, int index, String type) throws IOException {
        if (index < 0 || index >= palette.size()) {
            throw new IOException("Invalid Xaero " + type + " palette index");
        }
        return palette.get(index);
    }

    private static void write(RegionData data, Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new IOException("Xaero region destination has no parent");
        }
        Files.createDirectories(parent);
        try (ZipOutputStream zipOutput = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(
                destination,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        ), 32 * 1024))) {
            zipOutput.setLevel(Deflater.BEST_COMPRESSION);
            ZipEntry entry = new ZipEntry("region.xaero");
            entry.setTime(0L);
            zipOutput.putNextEntry(entry);
            DataOutputStream output = new DataOutputStream(zipOutput);
            output.writeByte(255);
            output.writeInt(FULL_VERSION);
            Map<CompoundTag, Integer> statePalette = new HashMap<>();
            Map<String, Integer> biomePalette = new HashMap<>();
            for (int chunkIndex = 0; chunkIndex < 64; chunkIndex++) {
                boolean present = false;
                for (int tile = 0; tile < 16; tile++) {
                    if (data.tiles.containsKey(chunkIndex * 16 + tile)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    continue;
                }
                output.writeByte((chunkIndex / 8) << 4 | chunkIndex % 8);
                for (int tile = 0; tile < 16; tile++) {
                    TileData tileData = data.tiles.get(chunkIndex * 16 + tile);
                    if (tileData == null) {
                        output.writeInt(-1);
                        continue;
                    }
                    for (PixelData pixel : tileData.pixels) {
                        writePixel(output, pixel, statePalette, biomePalette);
                    }
                    output.writeByte(tileData.interpretationVersion);
                    output.writeInt(tileData.caveStart);
                    output.writeByte(tileData.caveDepth);
                }
            }
            output.flush();
            zipOutput.closeEntry();
        }
        if (Files.size(destination) > XaeroArchiveLimits.MAX_REGION_COMPRESSED_SIZE) {
            Files.deleteIfExists(destination);
            throw new IOException("Merged Xaero region exceeds the compressed limit");
        }
    }

    private static void writePixel(
            DataOutputStream output,
            PixelData pixel,
            Map<CompoundTag, Integer> statePalette,
            Map<String, Integer> biomePalette
    ) throws IOException {
        int parameters = pixel.parameters & ~(STATE_PALETTE_NEW | BIOME_PALETTE_NEW | BIOME_AS_INT);
        Integer stateIndex = pixel.state == null ? null : statePalette.get(pixel.state);
        if (pixel.state != null && stateIndex == null) {
            parameters |= STATE_PALETTE_NEW;
        }
        Integer biomeIndex = pixel.biome == null ? null : biomePalette.get(pixel.biome);
        if (pixel.biome != null && biomeIndex == null) {
            parameters |= BIOME_PALETTE_NEW;
        }
        output.writeInt(parameters);
        if (pixel.state != null) {
            if (stateIndex == null) {
                NbtIo.write(pixel.state, output);
                statePalette.put(pixel.state, statePalette.size());
            } else {
                output.writeInt(stateIndex);
            }
        }
        if (pixel.topHeight != null) {
            output.writeByte(pixel.topHeight);
        }
        if (!pixel.overlays.isEmpty()) {
            output.writeByte(pixel.overlays.size());
            for (OverlayData overlay : pixel.overlays) {
                int overlayParameters = overlay.parameters & ~OVERLAY_PALETTE_NEW;
                Integer overlayIndex = overlay.state == null ? null : statePalette.get(overlay.state);
                if (overlay.state != null && overlayIndex == null) {
                    overlayParameters |= OVERLAY_PALETTE_NEW;
                }
                output.writeInt(overlayParameters);
                if (overlay.state != null) {
                    if (overlayIndex == null) {
                        NbtIo.write(overlay.state, output);
                        statePalette.put(overlay.state, statePalette.size());
                    } else {
                        output.writeInt(overlayIndex);
                    }
                }
            }
        }
        if (pixel.biome != null) {
            if (biomeIndex == null) {
                output.writeUTF(pixel.biome);
                biomePalette.put(pixel.biome, biomePalette.size());
            } else {
                output.writeInt(biomeIndex);
            }
        }
    }

    public record RegionStats(long compressedSize, long uncompressedSize, int tileCount) {
    }

    private record PixelData(
            int parameters,
            CompoundTag state,
            Byte topHeight,
            List<OverlayData> overlays,
            String biome
    ) {
    }

    private record OverlayData(int parameters, CompoundTag state) {
    }

    private record TileData(List<PixelData> pixels, byte interpretationVersion, int caveStart, byte caveDepth) {
    }

    private static final class RegionData {
        private final Map<Integer, TileData> tiles;
        private final long uncompressedSize;

        private RegionData(Map<Integer, TileData> tiles, long uncompressedSize) {
            this.tiles = tiles;
            this.uncompressedSize = uncompressedSize;
        }
    }

    private static final class BoundedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private BoundedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                add(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                add(read);
            }
            return read;
        }

        private void add(long amount) throws IOException {
            count += amount;
            if (count > limit) {
                throw new IOException("Xaero region uncompressed size exceeds the limit");
            }
        }

        private long count() {
            return count;
        }
    }

    private XaeroRegionCodec() {
    }
}
