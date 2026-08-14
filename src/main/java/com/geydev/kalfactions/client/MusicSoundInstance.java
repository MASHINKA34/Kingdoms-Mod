package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.ConstantFloat;

public final class MusicSoundInstance extends AbstractTickableSoundInstance {
    private final BlockPos speakerPos;
    private final Path file;
    private final float speakerVolume;
    private final int speakerRadius;

    public MusicSoundInstance(BlockPos speakerPos, String hash, Path file, float volume, int radius, boolean loop) {
        super(
                SoundEvent.createVariableRangeEvent(syntheticLocation(hash)),
                SoundSource.RECORDS,
                SoundInstance.createUnseededRandom()
        );
        this.speakerPos = speakerPos.immutable();
        this.file = file;
        this.speakerVolume = Mth.clamp(volume, 0.0F, 1.0F);
        this.speakerRadius = Math.max(1, radius);
        this.looping = loop;
        this.delay = 0;
        this.relative = false;
        this.attenuation = MusicClientSettings.linearAttenuation()
                ? SoundInstance.Attenuation.LINEAR
                : SoundInstance.Attenuation.NONE;
        this.x = speakerPos.getX() + 0.5D;
        this.y = speakerPos.getY() + 0.5D;
        this.z = speakerPos.getZ() + 0.5D;
        this.volume = computeVolume();
    }

    public BlockPos speakerPos() {
        return speakerPos;
    }

    public static ResourceLocation syntheticLocation(String hash) {
        return ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "music/" + hash);
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        this.sound = new Sound(
                location,
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                1,
                Sound.Type.FILE,
                true,
                false,
                speakerRadius
        );
        WeighedSoundEvents events = new WeighedSoundEvents(location, null);
        events.addSound(this.sound);
        return events;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                InputStream input = Files.newInputStream(file);
                return looping
                        ? (AudioStream) new LoopingAudioStream(JOrbisAudioStream::new, input)
                        : new JOrbisAudioStream(input);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, Util.nonCriticalIoPool());
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stop();
            return;
        }
        if (MusicClientSettings.isMuted(minecraft.level.dimension(), speakerPos)) {
            stop();
            return;
        }
        volume = computeVolume();
    }

    public void requestStop() {
        stop();
    }

    private float computeVolume() {
        float personal = MusicClientSettings.volume();
        float value = speakerVolume * personal;
        if (attenuation == SoundInstance.Attenuation.NONE) {
            value *= manualFalloff();
        }
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private float manualFalloff() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0.0F;
        }
        double distance = Math.sqrt(minecraft.player.distanceToSqr(x, y, z));
        if (distance >= speakerRadius) {
            return 0.0F;
        }
        float ratio = (float) (distance / speakerRadius);
        return (1.0F - ratio) * (1.0F - ratio);
    }
}
