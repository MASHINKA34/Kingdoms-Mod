package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class EmblemTextures {
    private static final int MAX_URL_BYTES = 64 * 1024;
    private static final int MAX_URL_IMAGE_SIZE = 256;
    private static final long FAILED_RETRY_MILLIS = 60_000L;
    private static final Map<UUID, PixelEntry> PIXEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UrlEntry> URL_CACHE = new ConcurrentHashMap<>();

    public record Emblem(ResourceLocation texture, int width, int height) {
    }

    public static Emblem resolve(UUID factionId, List<Integer> pixels, String url) {
        if (url != null && !url.isBlank()) {
            UrlEntry entry = URL_CACHE.compute(url, (key, existing) -> {
                if (existing == null
                        || (existing.state == UrlState.FAILED
                                && System.currentTimeMillis() - existing.failedAt > FAILED_RETRY_MILLIS)) {
                    download(key);
                    return new UrlEntry(UrlState.LOADING, null, 0L);
                }
                return existing;
            });
            if (entry.state == UrlState.READY) {
                return entry.emblem;
            }
        }
        if (factionId != null && pixels != null && pixels.size() == 256) {
            int hash = pixels.hashCode();
            PixelEntry entry = PIXEL_CACHE.get(factionId);
            if (entry == null || entry.hash != hash) {
                entry = new PixelEntry(hash, uploadPixels(factionId, pixels));
                PIXEL_CACHE.put(factionId, entry);
            }
            return entry.emblem;
        }
        return null;
    }

    private static Emblem uploadPixels(UUID factionId, List<Integer> pixels) {
        NativeImage image = new NativeImage(16, 16, true);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                Integer boxed = pixels.get(y * 16 + x);
                image.setPixelRGBA(x, y, argbToAbgr(boxed == null ? 0 : boxed));
            }
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                "emblem/px/" + factionId.toString().toLowerCase(Locale.ROOT)
        );
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return new Emblem(location, 16, 16);
    }

    private static void download(String url) {
        CompletableFuture.runAsync(() -> {
            byte[] data;
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "KingdomsMod");
                try (InputStream in = connection.getInputStream()) {
                    data = in.readNBytes(MAX_URL_BYTES + 1);
                }
                if (data.length > MAX_URL_BYTES) {
                    throw new IOException("Emblem larger than " + MAX_URL_BYTES + " bytes");
                }
                NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                if (image.getWidth() > MAX_URL_IMAGE_SIZE || image.getHeight() > MAX_URL_IMAGE_SIZE) {
                    image.close();
                    throw new IOException("Emblem image dimensions too large");
                }
                Minecraft.getInstance().execute(() -> {
                    ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                            KalFactions.MOD_ID,
                            "emblem/url/" + Integer.toHexString(url.hashCode())
                    );
                    Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
                    URL_CACHE.put(url, new UrlEntry(
                            UrlState.READY,
                            new Emblem(location, image.getWidth(), image.getHeight()),
                            0L
                    ));
                });
            } catch (Exception exception) {
                KalFactions.LOGGER.warn("Failed to load faction emblem from {}: {}", url, exception.toString());
                URL_CACHE.put(url, new UrlEntry(UrlState.FAILED, null, System.currentTimeMillis()));
            }
        }, Util.ioPool());
    }

    private static int argbToAbgr(int argb) {
        int alpha = argb >>> 24;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    private enum UrlState {
        LOADING,
        READY,
        FAILED
    }

    private record PixelEntry(int hash, Emblem emblem) {
    }

    private record UrlEntry(UrlState state, Emblem emblem, long failedAt) {
    }

    private EmblemTextures() {
    }
}
