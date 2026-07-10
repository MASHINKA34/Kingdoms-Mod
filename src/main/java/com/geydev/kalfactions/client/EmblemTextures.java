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
    private static final int MAX_URL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_URL_IMAGE_SIZE = 1024;
    private static final long FAILED_RETRY_MILLIS = 60_000L;
    private static final Map<UUID, PixelEntry> PIXEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UrlEntry> URL_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, Emblem> FALLBACK_CACHE = new ConcurrentHashMap<>();

    public record Emblem(ResourceLocation texture, int width, int height) {
    }

    public static Emblem resolve(UUID factionId, List<Integer> pixels, String url) {
        return resolve(factionId, pixels, url, null);
    }

    public static Emblem resolve(UUID factionId, List<Integer> pixels, String url, Integer fallbackColor) {
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
        if (factionId != null && pixels != null && isValidPixelCount(pixels.size())) {
            int hash = pixels.hashCode();
            PixelEntry entry = PIXEL_CACHE.get(factionId);
            if (entry == null || entry.hash != hash) {
                entry = new PixelEntry(hash, uploadPixels(factionId, pixels));
                PIXEL_CACHE.put(factionId, entry);
            }
            return entry.emblem;
        }
        return fallbackColor == null ? null : FALLBACK_CACHE.computeIfAbsent(fallbackColor & 0xFFFFFF, EmblemTextures::fallback);
    }

    public static boolean isValidPixelCount(int count) {
        return count == 256 || count == 1024;
    }

    private static Emblem uploadPixels(UUID factionId, List<Integer> pixels) {
        int size = (int) Math.sqrt(pixels.size());
        NativeImage image = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                Integer boxed = pixels.get(y * size + x);
                image.setPixelRGBA(x, y, argbToAbgr(boxed == null ? 0 : boxed));
            }
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                "emblem/px/" + factionId.toString().toLowerCase(Locale.ROOT)
        );
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return new Emblem(location, size, size);
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

    private static Emblem fallback(int color) {
        int size = 16;
        int base = 0xFF000000 | color;
        int light = mix(base, 0xFFFFFFFF, 0.35F);
        int dark = mix(base, 0xFF000000, 0.45F);
        NativeImage image = new NativeImage(size, size, true);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean shield = y < 2
                        ? x >= 2 && x <= 13
                        : y < 11
                                ? x >= 1 && x <= 14
                                : x >= y - 9 && x <= 24 - y;
                int pixel = 0;
                if (shield) {
                    boolean border = x <= 2 || x >= 13 || y <= 2 || y >= 13 || x == y - 8 || x == 23 - y;
                    boolean band = x - y >= -1 && x - y <= 1;
                    pixel = border ? 0xFF1A140C : band ? light : base;
                    if (!border && !band && (x + y) % 5 == 0) {
                        pixel = dark;
                    }
                }
                image.setPixelRGBA(x, y, argbToAbgr(pixel));
            }
        }
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                "emblem/fallback/" + Integer.toHexString(color)
        );
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return new Emblem(location, size, size);
    }

    private static int mix(int left, int right, float amount) {
        int r = blend((left >> 16) & 0xFF, (right >> 16) & 0xFF, amount);
        int g = blend((left >> 8) & 0xFF, (right >> 8) & 0xFF, amount);
        int b = blend(left & 0xFF, right & 0xFF, amount);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int blend(int left, int right, float amount) {
        return Math.round(left + (right - left) * amount);
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
