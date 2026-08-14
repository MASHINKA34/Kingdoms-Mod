package com.geydev.kalfactions.music;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record MusicSpeaker(
        ResourceKey<Level> dimension,
        BlockPos pos,
        String hash,
        String trackName,
        float volume,
        int radius,
        boolean loop,
        boolean playing,
        boolean redstone
) {
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS = "pos";
    private static final String TAG_HASH = "hash";
    private static final String TAG_TRACK_NAME = "trackName";
    private static final String TAG_VOLUME = "volume";
    private static final String TAG_RADIUS = "radius";
    private static final String TAG_LOOP = "loop";
    private static final String TAG_PLAYING = "playing";
    private static final String TAG_REDSTONE = "redstone";

    public MusicSpeaker {
        Objects.requireNonNull(dimension, "dimension");
        pos = Objects.requireNonNull(pos, "pos").immutable();
        hash = hash == null ? "" : hash;
        trackName = MusicLimits.sanitizeName(trackName);
        volume = MusicLimits.clampVolume(volume);
        radius = MusicLimits.clampRadius(radius);
    }

    public boolean hasTrack() {
        return !hash.isEmpty();
    }

    public boolean audible() {
        return playing && hasTrack();
    }

    public MusicSpeaker withPlaying(boolean value) {
        return new MusicSpeaker(dimension, pos, hash, trackName, volume, radius, loop, value, redstone);
    }

    public MusicSpeaker withTrack(String newHash, String newName) {
        return new MusicSpeaker(dimension, pos, newHash, newName, volume, radius, loop, playing, redstone);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, dimension.location().toString());
        tag.putLong(TAG_POS, pos.asLong());
        tag.putString(TAG_HASH, hash);
        tag.putString(TAG_TRACK_NAME, trackName);
        tag.putFloat(TAG_VOLUME, volume);
        tag.putInt(TAG_RADIUS, radius);
        tag.putBoolean(TAG_LOOP, loop);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putBoolean(TAG_REDSTONE, redstone);
        return tag;
    }

    public static MusicSpeaker load(CompoundTag tag) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
        if (dimensionId == null) {
            return null;
        }
        return new MusicSpeaker(
                ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId),
                BlockPos.of(tag.getLong(TAG_POS)),
                tag.getString(TAG_HASH),
                tag.getString(TAG_TRACK_NAME),
                tag.getFloat(TAG_VOLUME),
                tag.getInt(TAG_RADIUS),
                tag.getBoolean(TAG_LOOP),
                tag.getBoolean(TAG_PLAYING),
                tag.getBoolean(TAG_REDSTONE)
        );
    }
}
