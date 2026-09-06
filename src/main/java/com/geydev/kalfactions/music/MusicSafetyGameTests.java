package com.geydev.kalfactions.music;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.gametest.RegressionPlayers;
import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import com.geydev.kalfactions.registry.ModBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MusicSafetyGameTests {
    @GameTest(template = "empty", batch = "music_safety")
    public static void inFlightUploadReservesQuotaUntilCanceled(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.MUSIC_BLOCK.get().defaultBlockState());
        var player = RegressionPlayers.create(level, pos, 2).player();
        byte[] data = new byte[]{'O', 'g', 'g', 'S', 23, 45, 67};
        String hash = ArchiveHashing.sha256(data);
        String otherHash = ArchiveHashing.sha256(new byte[]{7});
        UUID sessionId = UUID.randomUUID();
        int oldLimit = ModConfigSpec.MUSIC_MAX_TRACKS.get();
        try {
            ModConfigSpec.MUSIC_MAX_TRACKS.set(MusicManager.get(level).trackCount() + 1);
            MusicService.beginUpload(player, new MusicPayloads.C2SBeginUpload(sessionId, pos, "Quota", data.length, hash));
            helper.assertValueEqual(MusicService.checkUpload(level.getServer(), otherHash, "Another", 7),
                    MusicService.UploadCheck.TOO_MANY_TRACKS, "receiving upload reserves track quota");
            MusicService.uploadChunk(player, new MusicPayloads.C2SUploadChunk(sessionId, 0, data));
            helper.assertValueEqual(MusicService.checkUpload(level.getServer(), otherHash, "Another", 7),
                    MusicService.UploadCheck.TOO_MANY_TRACKS, "finishing upload keeps quota reserved");
            MusicService.cancelUpload(player, sessionId);
            helper.assertValueEqual(MusicService.checkUpload(level.getServer(), otherHash, "Another", 7),
                    MusicService.UploadCheck.OK, "canceling finishing upload releases reservation");
        } finally {
            MusicService.forget(player.getUUID());
            ModConfigSpec.MUSIC_MAX_TRACKS.set(oldLimit);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music_safety")
    public static void onlyUploaderOrAdministratorMayDeleteTrack(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = RegressionPlayers.create(level, helper.absolutePos(BlockPos.ZERO), 0).player();
        var outsider = RegressionPlayers.create(level, helper.absolutePos(BlockPos.ZERO), 0).player();
        var admin = RegressionPlayers.create(level, helper.absolutePos(BlockPos.ZERO), 2).player();
        var track = new MusicTrack(ArchiveHashing.sha256(new byte[]{1}), "Ownership", 1,
                owner.getUUID(), "Owner", 1);
        helper.assertTrue(MusicService.canDelete(owner, track), "uploader can delete own track");
        helper.assertFalse(MusicService.canDelete(outsider, track), "other player cannot delete global track");
        helper.assertTrue(MusicService.canDelete(admin, track), "administrator can delete track");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music_safety")
    public static void uploadRemainsBoundToOriginalSpeakerAndDimension(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.MUSIC_BLOCK.get().defaultBlockState());
        var player = RegressionPlayers.create(level, pos, 2).player();
        var session = new MusicService.UploadSession(player, UUID.randomUUID(), pos, "Session", 4,
                ArchiveHashing.sha256(MusicLimits.OGG_SIGNATURE));
        helper.assertTrue(session.validFor(player), "initial upload context valid");
        player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
        helper.assertFalse(session.validFor(player), "distant player cannot finish upload");
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        player.setServerLevel(level.getServer().getLevel(Level.NETHER));
        helper.assertFalse(session.validFor(player), "changed dimension cannot finish upload");
        player.setServerLevel(level);
        helper.assertTrue(session.validFor(player), "original speaker still valid");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos, ModBlocks.MUSIC_BLOCK.get().defaultBlockState());
        helper.assertFalse(session.validFor(player), "replacement speaker is a different upload target");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "music_safety")
    public static void simultaneousUploadsUseSeparateStagingFilesAndBoundedReads(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        byte[] data = new byte[]{'O', 'g', 'g', 'S', 11, 24, 37};
        String hash = ArchiveHashing.sha256(data);
        Path first = MusicStorage.stage(server, hash, data).join();
        Path second = MusicStorage.stage(server, hash, data).join();
        try {
            helper.assertFalse(first.equals(second), "simultaneous uploads need distinct temporary files");
            MusicStorage.commit(server, hash, first);
            MusicStorage.commit(server, hash, second);
            helper.assertTrue(Arrays.equals(MusicStorage.read(server, hash, data.length, () -> true).join(), data),
                    "committed track reads without corruption");
            boolean rejectedSize = false;
            try {
                MusicStorage.read(server, hash, data.length - 1L, () -> true).join();
            } catch (CompletionException expected) {
                rejectedSize = true;
            }
            helper.assertTrue(rejectedSize, "reader rejects unexpected file size");
            Files.write(MusicStorage.trackFile(server, hash), new byte[data.length]);
            boolean rejectedHash = false;
            try {
                MusicStorage.read(server, hash, data.length, () -> true).join();
            } catch (CompletionException expected) {
                rejectedHash = true;
            }
            helper.assertTrue(rejectedHash, "reader rejects corrupted content");
        } finally {
            Files.deleteIfExists(first);
            Files.deleteIfExists(second);
            MusicStorage.delete(server, hash);
        }
        helper.succeed();
    }

    private MusicSafetyGameTests() {
    }
}
