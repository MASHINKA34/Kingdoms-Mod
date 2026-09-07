package com.geydev.kalfactions.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class SavedDataFormat {
    public static final String TAG_VERSION = "formatVersion";
    public static final int LEGACY_VERSION = 0;

    private final int currentVersion;
    private final String versionKey;
    private final List<Migration> migrations = new ArrayList<>();

    public SavedDataFormat(int currentVersion) {
        this(currentVersion, TAG_VERSION);
    }

    public SavedDataFormat(int currentVersion, String versionKey) {
        if (currentVersion < 1) {
            throw new IllegalArgumentException("Saved data version must start at 1");
        }
        if (versionKey == null || versionKey.isBlank()) {
            throw new IllegalArgumentException("Saved data version key must not be blank");
        }
        this.currentVersion = currentVersion;
        this.versionKey = versionKey;
    }

    public String versionKey() {
        return versionKey;
    }

    public SavedDataFormat migration(int fromVersion, UnaryOperator<CompoundTag> step) {
        if (fromVersion < LEGACY_VERSION || fromVersion >= currentVersion) {
            throw new IllegalArgumentException("Migration " + fromVersion + " is outside 0.." + currentVersion);
        }
        migrations.add(new Migration(fromVersion, step));
        return this;
    }

    public int currentVersion() {
        return currentVersion;
    }

    public int versionOf(CompoundTag tag) {
        return tag.contains(versionKey, Tag.TAG_INT) ? tag.getInt(versionKey) : LEGACY_VERSION;
    }

    public boolean outdated(CompoundTag tag) {
        return versionOf(tag) != currentVersion;
    }

    public CompoundTag stamp(CompoundTag tag) {
        tag.putInt(versionKey, currentVersion);
        return tag;
    }

    public CompoundTag upgrade(CompoundTag tag) {
        CompoundTag upgraded = tag;
        for (int version = versionOf(tag); version < currentVersion; version++) {
            for (Migration migration : migrations) {
                if (migration.fromVersion == version) {
                    upgraded = migration.step.apply(upgraded);
                }
            }
        }
        return upgraded;
    }

    private record Migration(int fromVersion, UnaryOperator<CompoundTag> step) {
    }
}
