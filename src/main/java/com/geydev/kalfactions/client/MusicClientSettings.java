package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class MusicClientSettings {
    private static final String FILE_NAME = "music-settings.json";
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_LINEAR = "linearAttenuation";
    private static final String KEY_SILENCED = "silencedSpeakers";

    private static final Set<String> SILENCED = new LinkedHashSet<>();
    private static float volume = 1.0F;
    private static boolean muted;
    private static boolean linearAttenuation = true;
    private static boolean loaded;

    public static synchronized float volume() {
        ensureLoaded();
        return muted ? 0.0F : volume;
    }

    public static synchronized void setVolume(float value) {
        ensureLoaded();
        volume = Mth.clamp(value, 0.0F, 1.0F);
        save();
    }

    public static synchronized boolean muted() {
        ensureLoaded();
        return muted;
    }

    public static synchronized void setMuted(boolean value) {
        ensureLoaded();
        if (muted != value) {
            muted = value;
            save();
        }
    }

    public static synchronized boolean linearAttenuation() {
        ensureLoaded();
        return linearAttenuation;
    }

    public static synchronized boolean isMuted(ResourceKey<Level> dimension, BlockPos pos) {
        ensureLoaded();
        return muted || SILENCED.contains(key(dimension, pos));
    }

    public static synchronized boolean isSilenced(ResourceKey<Level> dimension, BlockPos pos) {
        ensureLoaded();
        return SILENCED.contains(key(dimension, pos));
    }

    public static synchronized void setSilenced(ResourceKey<Level> dimension, BlockPos pos, boolean value) {
        ensureLoaded();
        String key = key(dimension, pos);
        boolean changed = value ? SILENCED.add(key) : SILENCED.remove(key);
        if (changed) {
            save();
        }
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "@" + pos.asLong();
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolve(KalFactions.MOD_ID)
                .resolve(FILE_NAME);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();
            if (root.has(KEY_VOLUME)) {
                volume = Mth.clamp(root.get(KEY_VOLUME).getAsFloat(), 0.0F, 1.0F);
            }
            if (root.has(KEY_MUTED)) {
                muted = root.get(KEY_MUTED).getAsBoolean();
            }
            if (root.has(KEY_LINEAR)) {
                linearAttenuation = root.get(KEY_LINEAR).getAsBoolean();
            }
            if (root.has(KEY_SILENCED) && root.get(KEY_SILENCED).isJsonArray()) {
                JsonArray array = root.getAsJsonArray(KEY_SILENCED);
                for (JsonElement element : array) {
                    SILENCED.add(element.getAsString());
                }
            }
        } catch (IOException | RuntimeException exception) {
            KalFactions.LOGGER.warn("Failed to read music settings", exception);
        }
    }

    private static void save() {
        JsonObject root = new JsonObject();
        root.addProperty(KEY_VOLUME, volume);
        root.addProperty(KEY_MUTED, muted);
        root.addProperty(KEY_LINEAR, linearAttenuation);
        JsonArray array = new JsonArray();
        SILENCED.forEach(array::add);
        root.add(KEY_SILENCED, array);
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Failed to write music settings", exception);
        }
    }

    private MusicClientSettings() {
    }
}
