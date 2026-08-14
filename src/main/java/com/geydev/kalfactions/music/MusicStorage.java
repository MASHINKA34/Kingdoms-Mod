package com.geydev.kalfactions.music;

import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class MusicStorage {
    private static final String DIRECTORY = "kingdoms";
    private static final String MUSIC = "music";

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .resolve(DIRECTORY)
                .resolve(MUSIC);
    }

    public static Path trackFile(MinecraftServer server, String hash) {
        if (!ArchiveHashing.isSha256(hash)) {
            throw new IllegalArgumentException("Invalid music track hash");
        }
        return root(server).resolve(hash + ".ogg");
    }

    public static boolean exists(MinecraftServer server, String hash) {
        return ArchiveHashing.isSha256(hash) && Files.isRegularFile(trackFile(server, hash));
    }

    public static CompletableFuture<Void> write(MinecraftServer server, String hash, byte[] data) {
        Path target = trackFile(server, hash);
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path temporary = target.resolveSibling(hash + ".ogg.tmp");
                Files.write(temporary, data);
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, Util.ioPool());
    }

    public static CompletableFuture<byte[]> read(MinecraftServer server, String hash) {
        Path source = trackFile(server, hash);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.readAllBytes(source);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }, Util.ioPool());
    }

    public static void deleteAsync(MinecraftServer server, String hash) {
        if (!ArchiveHashing.isSha256(hash)) {
            return;
        }
        Path target = trackFile(server, hash);
        CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
        }, Util.ioPool());
    }

    private MusicStorage() {
    }
}
