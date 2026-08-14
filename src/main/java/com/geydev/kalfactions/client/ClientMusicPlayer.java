package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.music.MusicLimits;
import com.geydev.kalfactions.music.MusicPayloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientMusicPlayer {
    private static final String CACHE_DIRECTORY = "music-cache";
    private static final int HEALTH_CHECK_TICKS = 40;

    private static final Map<Long, Speaker> SPEAKERS = new LinkedHashMap<>();
    private static final Map<String, Download> DOWNLOADS = new HashMap<>();
    private static int healthCheckCounter;

    public static void handleStart(MusicPayloads.S2CSpeakerStart payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (!ArchiveHashing.isSha256(payload.hash())) {
                return;
            }
            long key = payload.pos().asLong();
            Speaker existing = SPEAKERS.get(key);
            if (existing != null) {
                existing.stop();
            }
            Speaker speaker = new Speaker(
                    payload.pos().immutable(),
                    payload.hash(),
                    payload.volume(),
                    payload.radius(),
                    payload.loop()
            );
            SPEAKERS.put(key, speaker);
            startOrDownload(speaker);
        });
    }

    public static void handleStop(MusicPayloads.S2CSpeakerStop payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Speaker speaker = SPEAKERS.remove(payload.pos().asLong());
            if (speaker != null) {
                speaker.stop();
            }
        });
    }

    public static void handleStopAll() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(ClientMusicPlayer::stopAll);
    }

    public static void handleTrackBegin(MusicPayloads.S2CTrackBegin payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (payload.totalBytes() <= 0 || payload.totalBytes() > MusicLimits.maxTrackBytes()) {
                DOWNLOADS.remove(payload.hash());
                return;
            }
            DOWNLOADS.put(payload.hash(), new Download(payload.totalBytes()));
        });
    }

    public static void handleTrackChunk(MusicPayloads.S2CTrackChunk payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            Download download = DOWNLOADS.get(payload.hash());
            if (download == null || !download.accept(payload.index(), payload.data())) {
                DOWNLOADS.remove(payload.hash());
                return;
            }
            if (!download.complete()) {
                return;
            }
            DOWNLOADS.remove(payload.hash());
            if (!ArchiveHashing.sha256(download.buffer).equals(payload.hash())) {
                notice("kingdoms.music.error.checksum");
                return;
            }
            writeCache(payload.hash(), download.buffer);
        });
    }

    public static void handleTrackFailed(MusicPayloads.S2CTrackFailed payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            DOWNLOADS.remove(payload.hash());
            notice(payload.messageKey());
        });
    }

    public static void handleMute(boolean muted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            MusicClientSettings.setMuted(muted);
            if (muted) {
                for (Speaker speaker : SPEAKERS.values()) {
                    speaker.stop();
                }
            } else {
                for (Speaker speaker : SPEAKERS.values()) {
                    startOrDownload(speaker);
                }
            }
        });
    }

    public static void refreshSpeaker(BlockPos pos) {
        Speaker speaker = SPEAKERS.get(pos.asLong());
        if (speaker == null) {
            return;
        }
        speaker.stop();
        startOrDownload(speaker);
    }

    public static boolean isCached(String hash) {
        return ArchiveHashing.isSha256(hash) && Files.isRegularFile(cacheFile(hash));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            if (!SPEAKERS.isEmpty()) {
                stopAll();
            }
            return;
        }
        if (SPEAKERS.isEmpty()) {
            return;
        }
        if (++healthCheckCounter < HEALTH_CHECK_TICKS) {
            return;
        }
        healthCheckCounter = 0;
        if (minecraft.options.getSoundSourceVolume(SoundSource.RECORDS) <= 0.0F || MusicClientSettings.muted()) {
            return;
        }
        for (Speaker speaker : List.copyOf(SPEAKERS.values())) {
            if (speaker.needsRestart(minecraft)) {
                startOrDownload(speaker);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAll();
    }

    private static void stopAll() {
        for (Speaker speaker : SPEAKERS.values()) {
            speaker.stop();
        }
        SPEAKERS.clear();
        DOWNLOADS.clear();
        healthCheckCounter = 0;
    }

    private static void startOrDownload(Speaker speaker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (MusicClientSettings.isMuted(minecraft.level.dimension(), speaker.pos)) {
            return;
        }
        Path file = cacheFile(speaker.hash);
        if (!Files.isRegularFile(file)) {
            requestTrack(speaker.hash);
            return;
        }
        speaker.start(minecraft, file);
    }

    private static void requestTrack(String hash) {
        if (DOWNLOADS.containsKey(hash)) {
            return;
        }
        DOWNLOADS.put(hash, Download.PENDING);
        PacketDistributor.sendToServer(new MusicPayloads.C2SRequestTrack(hash));
    }

    private static void writeCache(String hash, byte[] data) {
        Path target = cacheFile(hash);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path temporary = target.resolveSibling(hash + ".ogg.tmp");
                Files.write(temporary, data);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }, Util.ioPool()).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                KalFactions.LOGGER.warn("Failed to cache music track {}", hash, error);
                notice("kingdoms.music.error.cache_failed");
                return;
            }
            for (Speaker speaker : List.copyOf(SPEAKERS.values())) {
                if (speaker.hash.equals(hash) && speaker.instance == null) {
                    startOrDownload(speaker);
                }
            }
        }));
    }

    private static Path cacheFile(String hash) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .toAbsolutePath()
                .normalize()
                .resolve(KalFactions.MOD_ID)
                .resolve(CACHE_DIRECTORY)
                .resolve(hash + ".ogg");
    }

    private static void notice(String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !messageKey.isEmpty()) {
            minecraft.player.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    private static final class Speaker {
        private final BlockPos pos;
        private final String hash;
        private final float volume;
        private final int radius;
        private final boolean loop;
        private MusicSoundInstance instance;

        private Speaker(BlockPos pos, String hash, float volume, int radius, boolean loop) {
            this.pos = pos;
            this.hash = hash;
            this.volume = volume;
            this.radius = radius;
            this.loop = loop;
        }

        private void start(Minecraft minecraft, Path file) {
            stop();
            instance = new MusicSoundInstance(pos, hash, file, volume, radius, loop);
            minecraft.getSoundManager().play(instance);
        }

        private void stop() {
            if (instance == null) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            instance.requestStop();
            minecraft.getSoundManager().stop(instance);
            instance = null;
        }

        private boolean needsRestart(Minecraft minecraft) {
            if (instance == null) {
                return !DOWNLOADS.containsKey(hash);
            }
            if (minecraft.getSoundManager().isActive(instance)) {
                return false;
            }
            instance = null;
            return true;
        }
    }

    private static final class Download {
        private static final Download PENDING = new Download(0);

        private final byte[] buffer;
        private int expectedIndex;
        private int received;

        private Download(int total) {
            this.buffer = new byte[Math.max(0, total)];
        }

        private boolean accept(int index, byte[] data) {
            if (this == PENDING || index != expectedIndex || data.length == 0) {
                return false;
            }
            if (received + data.length > buffer.length) {
                return false;
            }
            System.arraycopy(data, 0, buffer, received, data.length);
            received += data.length;
            expectedIndex++;
            return true;
        }

        private boolean complete() {
            return this != PENDING && received == buffer.length && buffer.length > 0;
        }
    }

    private ClientMusicPlayer() {
    }
}
