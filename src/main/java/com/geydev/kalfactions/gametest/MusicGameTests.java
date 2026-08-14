package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.music.MusicChunkBuffer;
import com.geydev.kalfactions.music.MusicLimits;
import com.geydev.kalfactions.music.MusicManager;
import com.geydev.kalfactions.music.MusicService;
import com.geydev.kalfactions.music.MusicSpeaker;
import com.geydev.kalfactions.music.MusicStorage;
import com.geydev.kalfactions.music.MusicTrack;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MusicGameTests {
    @GameTest(template = "empty", batch = "music")
    public static void chunkedUploadRebuildsFileAndHash(GameTestHelper helper) {
        byte[] source = sampleTrack(MusicLimits.CHUNK_SIZE * 2 + 777);
        String expected = ArchiveHashing.sha256(source);
        MusicChunkBuffer buffer = new MusicChunkBuffer(source.length);
        int index = 0;
        int offset = 0;
        while (offset < source.length) {
            int length = Math.min(MusicLimits.CHUNK_SIZE, source.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(source, offset, chunk, 0, length);
            if (!buffer.accept(index, chunk)) {
                throw new IllegalStateException("Music chunk " + index + " was rejected");
            }
            offset += length;
            index++;
        }
        helper.assertTrue(buffer.complete(), "assembled music buffer is complete");
        helper.assertValueEqual(buffer.received(), source.length, "assembled music byte count");
        helper.assertValueEqual(ArchiveHashing.sha256(buffer.data()), expected, "assembled music checksum");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music")
    public static void outOfOrderAndOversizedChunksAreRejected(GameTestHelper helper) {
        MusicChunkBuffer buffer = new MusicChunkBuffer(64);
        helper.assertFalse(buffer.accept(1, new byte[64]), "out of order music chunk accepted");
        helper.assertFalse(buffer.accept(0, new byte[65]), "oversized music chunk accepted");
        helper.assertTrue(buffer.accept(0, new byte[64]), "valid music chunk rejected");
        helper.assertFalse(buffer.accept(1, new byte[1]), "music chunk past the declared size accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music")
    public static void oversizedUploadIsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String checksum = ArchiveHashing.sha256(sampleTrack(32));
        MusicService.UploadCheck check = MusicService.checkUpload(
                server, checksum, "too big", MusicLimits.maxTrackBytes() + 1);
        helper.assertValueEqual(check, MusicService.UploadCheck.TOO_LARGE, "oversized upload verdict");
        helper.assertValueEqual(
                MusicService.checkUpload(server, checksum, "empty", 0),
                MusicService.UploadCheck.TOO_LARGE,
                "empty upload verdict"
        );
        helper.assertValueEqual(
                MusicService.checkUpload(server, "not-a-hash", "bad hash", 1024),
                MusicService.UploadCheck.INVALID_CHECKSUM,
                "malformed checksum verdict"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music")
    public static void identicalTracksAreDeduplicated(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MusicManager manager = MusicManager.get(server);
        byte[] source = sampleTrack(4096);
        String hash = ArchiveHashing.sha256(source);
        MusicService.deleteTrack(server, hash);
        try {
            MusicStorage.write(server, hash, source).join();
            MusicTrack track = new MusicTrack(hash, "dedupe", source.length, UUID.randomUUID(), "tester", 1L);
            helper.assertTrue(manager.addTrack(track), "first music track registration");
            int countAfterFirst = manager.trackCount();
            long bytesAfterFirst = manager.totalBytes();
            helper.assertFalse(
                    manager.addTrack(new MusicTrack(hash, "dedupe copy", source.length, null, "tester", 2L)),
                    "duplicate music track registration"
            );
            helper.assertValueEqual(manager.trackCount(), countAfterFirst, "music track count after duplicate");
            helper.assertValueEqual(manager.totalBytes(), bytesAfterFirst, "stored music bytes after duplicate");
            helper.assertValueEqual(
                    MusicService.checkUpload(server, hash, "dedupe copy", source.length),
                    MusicService.UploadCheck.ALREADY_PRESENT,
                    "duplicate upload verdict"
            );
        } finally {
            MusicService.deleteTrack(server, hash);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music")
    public static void speakerIsAudibleOnlyInsideItsRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MusicManager manager = MusicManager.get(level);
        BlockPos speakerPos = new BlockPos(1_500_000, 70, 1_500_000);
        manager.removeSpeaker(level.dimension(), speakerPos);
        try {
            MusicSpeaker speaker = new MusicSpeaker(
                    level.dimension(), speakerPos, sampleHash(), "radius", 1.0F, 100, true, true, true);
            manager.putSpeaker(speaker);
            List<MusicSpeaker> inside = audibleAt(level, speakerPos, 90.0D);
            helper.assertValueEqual(inside.size(), 1, "speakers audible at 90 blocks");
            List<MusicSpeaker> outside = audibleAt(level, speakerPos, 110.0D);
            helper.assertValueEqual(outside.size(), 0, "speakers audible at 110 blocks");
            manager.putSpeaker(speaker.withPlaying(false));
            helper.assertValueEqual(
                    audibleAt(level, speakerPos, 10.0D).size(), 0, "stopped speaker is silent");
        } finally {
            manager.removeSpeaker(level.dimension(), speakerPos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music")
    public static void registrySurvivesSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        MusicManager manager = MusicManager.get(server);
        BlockPos speakerPos = new BlockPos(1_400_000, 64, 1_400_000);
        String hash = sampleHash();
        manager.removeSpeaker(level.dimension(), speakerPos);
        manager.removeTrack(hash);
        try {
            manager.addTrack(new MusicTrack(hash, "persisted", 2048L, UUID.randomUUID(), "tester", 42L));
            manager.putSpeaker(new MusicSpeaker(
                    level.dimension(), speakerPos, hash, "persisted", 0.5F, 123, false, true, false));
            HolderLookup.Provider registries = server.registryAccess();
            CompoundTag tag = manager.save(new CompoundTag(), registries);
            MusicManager reloaded = MusicManager.FACTORY.deserializer().apply(tag, registries);
            MusicTrack track = reloaded.track(hash).orElseThrow();
            helper.assertValueEqual(track.name(), "persisted", "reloaded music track name");
            helper.assertValueEqual(track.size(), 2048L, "reloaded music track size");
            MusicSpeaker speaker = reloaded.speaker(level.dimension(), speakerPos).orElseThrow();
            helper.assertValueEqual(speaker.trackName(), "persisted", "reloaded speaker track name");
            helper.assertValueEqual(speaker.radius(), 123, "reloaded speaker radius");
            helper.assertValueEqual(speaker.volume(), 0.5F, "reloaded speaker volume");
            helper.assertFalse(speaker.loop(), "reloaded speaker loop flag");
            helper.assertTrue(speaker.playing(), "reloaded speaker playing flag");
            helper.assertFalse(speaker.redstone(), "reloaded speaker redstone flag");
        } finally {
            manager.removeSpeaker(level.dimension(), speakerPos);
            manager.removeTrack(hash);
        }
        helper.succeed();
    }

    private static List<MusicSpeaker> audibleAt(ServerLevel level, BlockPos speakerPos, double offset) {
        Vec3 position = new Vec3(speakerPos.getX() + 0.5D + offset, speakerPos.getY() + 0.5D, speakerPos.getZ() + 0.5D);
        return com.geydev.kalfactions.music.MusicRadius.audibleAt(level, position);
    }

    private static byte[] sampleTrack(int length) {
        byte[] data = new byte[length];
        System.arraycopy(MusicLimits.OGG_SIGNATURE, 0, data, 0, MusicLimits.OGG_SIGNATURE.length);
        for (int index = MusicLimits.OGG_SIGNATURE.length; index < length; index++) {
            data[index] = (byte) (index * 31 + 7);
        }
        return data;
    }

    private static String sampleHash() {
        return ArchiveHashing.sha256(new byte[]{'k', 'i', 'n', 'g', 'd', 'o', 'm', 's'});
    }

    private MusicGameTests() {
    }
}
