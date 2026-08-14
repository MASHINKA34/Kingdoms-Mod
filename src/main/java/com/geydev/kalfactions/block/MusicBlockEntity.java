package com.geydev.kalfactions.block;

import com.geydev.kalfactions.music.MusicLimits;
import com.geydev.kalfactions.music.MusicManager;
import com.geydev.kalfactions.music.MusicSpeaker;
import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MusicBlockEntity extends BlockEntity {
    private static final String TAG_HASH = "TrackHash";
    private static final String TAG_NAME = "TrackName";
    private static final String TAG_VOLUME = "Volume";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_LOOP = "Loop";
    private static final String TAG_PLAYING = "Playing";
    private static final String TAG_REDSTONE = "Redstone";

    private String hash = "";
    private String trackName = "";
    private float volume = 1.0F;
    private int radius = MusicLimits.defaultRadius();
    private boolean loop = true;
    private boolean playing;
    private boolean redstone = true;

    public MusicBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSIC_BLOCK.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            MusicManager manager = MusicManager.get(serverLevel);
            manager.speaker(serverLevel.dimension(), worldPosition)
                    .ifPresentOrElse(this::applySpeaker, () -> manager.putSpeaker(toSpeaker(serverLevel)));
        }
    }

    public MusicSpeaker toSpeaker(ServerLevel serverLevel) {
        return new MusicSpeaker(
                serverLevel.dimension(),
                worldPosition,
                hash,
                trackName,
                volume,
                radius,
                loop,
                playing,
                redstone
        );
    }

    public void applySpeaker(MusicSpeaker speaker) {
        hash = speaker.hash();
        trackName = speaker.trackName();
        volume = speaker.volume();
        radius = speaker.radius();
        loop = speaker.loop();
        playing = speaker.playing();
        redstone = speaker.redstone();
        setChanged();
    }

    public boolean redstone() {
        return redstone;
    }

    public boolean playing() {
        return playing;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hash = tag.getString(TAG_HASH);
        trackName = tag.getString(TAG_NAME);
        volume = tag.contains(TAG_VOLUME) ? MusicLimits.clampVolume(tag.getFloat(TAG_VOLUME)) : 1.0F;
        radius = tag.contains(TAG_RADIUS)
                ? MusicLimits.clampRadius(tag.getInt(TAG_RADIUS))
                : MusicLimits.defaultRadius();
        loop = !tag.contains(TAG_LOOP) || tag.getBoolean(TAG_LOOP);
        playing = tag.getBoolean(TAG_PLAYING);
        redstone = !tag.contains(TAG_REDSTONE) || tag.getBoolean(TAG_REDSTONE);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_HASH, hash);
        tag.putString(TAG_NAME, trackName);
        tag.putFloat(TAG_VOLUME, volume);
        tag.putInt(TAG_RADIUS, radius);
        tag.putBoolean(TAG_LOOP, loop);
        tag.putBoolean(TAG_PLAYING, playing);
        tag.putBoolean(TAG_REDSTONE, redstone);
    }
}
