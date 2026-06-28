package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientWorldMapStore {
    private static final ResourceLocation TEXTURE_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "worldmap/overview");
    private static final long REQUEST_COOLDOWN_MILLIS = 5_000L;

    private static byte[] buffer;
    private static int totalBytes;
    private static int received;
    private static volatile long assemblingVersion = -1L;

    private static volatile ResourceLocation texture;
    private static volatile long textureVersion = Long.MIN_VALUE;
    private static volatile int centerX;
    private static volatile int centerZ;
    private static volatile int regionBlocks;
    private static volatile int resolution;

    private static long lastRequest = Long.MIN_VALUE;
    private static long lastUnavailable = Long.MIN_VALUE;

    private ClientWorldMapStore() {
    }

    public static void clear() {
        buffer = null;
        assemblingVersion = -1L;
        texture = null;
        textureVersion = Long.MIN_VALUE;
        lastRequest = Long.MIN_VALUE;
        lastUnavailable = Long.MIN_VALUE;
    }

    public static void handleBegin(FactionPayloads.S2CWorldMapBegin payload) {
        if (payload.totalParts() <= 0 || payload.totalBytes() <= 0) {
            buffer = null;
            lastUnavailable = System.currentTimeMillis();
            return;
        }
        assemblingVersion = payload.version();
        totalBytes = payload.totalBytes();
        buffer = new byte[totalBytes];
        received = 0;
        centerX = payload.centerX();
        centerZ = payload.centerZ();
        regionBlocks = payload.regionBlocks();
        resolution = payload.width();
    }

    public static void handlePart(FactionPayloads.S2CWorldMapPart payload) {
        if (buffer == null || payload.version() != assemblingVersion) {
            return;
        }
        byte[] data = payload.data();
        int offset = payload.index() * FactionPayloads.S2CWorldMapPart.MAX_PART;
        if (offset < 0 || offset + data.length > buffer.length) {
            return;
        }
        System.arraycopy(data, 0, buffer, offset, data.length);
        received += data.length;
        if (received >= totalBytes) {
            build(buffer, assemblingVersion);
            buffer = null;
        }
    }

    private static void build(byte[] data, long version) {
        final NativeImage image;
        try {
            image = NativeImage.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            KalFactions.LOGGER.error("Failed to decode world map image", e);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, new DynamicTexture(image));
            texture = TEXTURE_ID;
            textureVersion = version;
        });
    }

    public static ResourceLocation texture() {
        return texture != null && textureVersion == assemblingVersion ? texture : null;
    }

    public static void requestIfNeeded(BlockPos pos) {
        if (texture() != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRequest < REQUEST_COOLDOWN_MILLIS || now - lastUnavailable < REQUEST_COOLDOWN_MILLIS) {
            return;
        }
        lastRequest = now;
        PacketDistributor.sendToServer(new FactionPayloads.C2SRequestWorldMap(pos));
    }

    public static int centerX() {
        return centerX;
    }

    public static int centerZ() {
        return centerZ;
    }

    public static int regionBlocks() {
        return regionBlocks;
    }

    public static int resolution() {
        return resolution;
    }
}
