package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.Locale;
import java.util.Optional;

public enum ScoutPackage {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    private final String id;

    ScoutPackage(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public int sizeChunks() {
        return switch (this) {
            case SMALL -> ModConfigSpec.SCOUT_SMALL_SIZE.getAsInt();
            case MEDIUM -> ModConfigSpec.SCOUT_MEDIUM_SIZE.getAsInt();
            case LARGE -> ModConfigSpec.SCOUT_LARGE_SIZE.getAsInt();
        };
    }

    public int durationMinutes() {
        return switch (this) {
            case SMALL -> ModConfigSpec.SCOUT_SMALL_MINUTES.getAsInt();
            case MEDIUM -> ModConfigSpec.SCOUT_MEDIUM_MINUTES.getAsInt();
            case LARGE -> ModConfigSpec.SCOUT_LARGE_MINUTES.getAsInt();
        };
    }

    public long price() {
        return switch (this) {
            case SMALL -> ModConfigSpec.SCOUT_SMALL_PRICE.get();
            case MEDIUM -> ModConfigSpec.SCOUT_MEDIUM_PRICE.get();
            case LARGE -> ModConfigSpec.SCOUT_LARGE_PRICE.get();
        };
    }

    public long durationMillis() {
        return durationMinutes() * 60_000L;
    }

    public int blocks() {
        return sizeChunks() * 16;
    }

    public int chunkCount() {
        return sizeChunks() * sizeChunks();
    }

    public static ScoutPackage byOrdinal(int ordinal) {
        ScoutPackage[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }

    public static Optional<ScoutPackage> byId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ScoutPackage option : values()) {
            if (option.id.equals(normalized)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }
}
