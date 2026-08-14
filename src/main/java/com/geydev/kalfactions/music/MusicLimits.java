package com.geydev.kalfactions.music;

import com.geydev.kalfactions.config.ModConfigSpec;

public final class MusicLimits {
    public static final String PROTOCOL_VERSION = "1";
    public static final int CHUNK_SIZE = 24 * 1024;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_TRACK_ENTRIES = 4096;
    public static final int MAX_SPEAKER_ENTRIES = 4096;
    public static final int MAX_ACTIVE_SPEAKERS_PER_CLIENT = 3;
    public static final long SESSION_TIMEOUT_MILLIS = 60_000L;
    public static final long ACTION_COOLDOWN_TICKS = 5L;
    public static final int HARD_MAX_TRACK_BYTES = 64 * 1024 * 1024;
    public static final int RADIUS_TICK_INTERVAL = 20;
    public static final byte[] OGG_SIGNATURE = {'O', 'g', 'g', 'S'};

    public static int maxTrackBytes() {
        return ModConfigSpec.MUSIC_MAX_TRACK_BYTES.getAsInt();
    }

    public static int maxTracks() {
        return ModConfigSpec.MUSIC_MAX_TRACKS.getAsInt();
    }

    public static long maxStorageBytes() {
        return (long) ModConfigSpec.MUSIC_MAX_STORAGE_MEGABYTES.getAsInt() * 1024L * 1024L;
    }

    public static long downloadBytesPerSecond() {
        return (long) ModConfigSpec.MUSIC_DOWNLOAD_KILOBYTES_PER_SECOND.getAsInt() * 1024L;
    }

    public static int maxRadius() {
        return ModConfigSpec.MUSIC_MAX_RADIUS.getAsInt();
    }

    public static int defaultRadius() {
        return Math.min(ModConfigSpec.MUSIC_DEFAULT_RADIUS.getAsInt(), maxRadius());
    }

    public static int clampRadius(int radius) {
        return Math.clamp(radius, 1, maxRadius());
    }

    public static float clampVolume(float volume) {
        return Float.isFinite(volume) ? Math.clamp(volume, 0.0F, 1.0F) : 1.0F;
    }

    public static boolean hasOggSignature(byte[] data) {
        if (data == null || data.length < OGG_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < OGG_SIGNATURE.length; index++) {
            if (data[index] != OGG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    public static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(MAX_NAME_LENGTH);
        for (int index = 0; index < name.length() && builder.length() < MAX_NAME_LENGTH; index++) {
            char character = name.charAt(index);
            if (character >= ' ' && character != 127) {
                builder.append(character);
            }
        }
        return builder.toString().trim();
    }

    private MusicLimits() {
    }
}
