package com.geydev.kalfactions.music;

import com.geydev.kalfactions.integration.xaero.archive.ArchiveHashing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class MusicStorage {
    private static final String DIRECTORY = "kingdoms";
    private static final String MUSIC = "music";
    private static final long MAX_STAGED_BYTES = 2L * MusicLimits.HARD_MAX_TRACK_BYTES;
    private static final AtomicLong STAGED_BYTES = new AtomicLong();
    private static final ThreadPoolExecutor IO_EXECUTOR = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16), runnable -> {
                Thread thread = new Thread(runnable, "kingdoms-music-io");
                thread.setDaemon(true);
                return thread;
            });

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
        return stage(server, hash, data).thenAccept(staged -> commit(server, hash, staged));
    }

    static CompletableFuture<Path> stage(MinecraftServer server, String hash, byte[] data) {
        Path target = trackFile(server, hash);
        long pending = STAGED_BYTES.getAndUpdate(current ->
                data.length <= MAX_STAGED_BYTES - current ? current + data.length : current);
        if (data.length > MAX_STAGED_BYTES - pending) {
            return CompletableFuture.failedFuture(new IOException("Too many pending music writes"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                Path temporary = null;
                try {
                    Files.createDirectories(target.getParent());
                    temporary = Files.createTempFile(target.getParent(), hash + "-", ".tmp");
                    Files.write(temporary, data);
                    return temporary;
                } catch (IOException exception) {
                    discard(temporary);
                    throw new UncheckedIOException(exception);
                } finally {
                    STAGED_BYTES.addAndGet(-data.length);
                }
            }, IO_EXECUTOR);
        } catch (RejectedExecutionException exception) {
            STAGED_BYTES.addAndGet(-data.length);
            return CompletableFuture.failedFuture(exception);
        }
    }

    static void commit(MinecraftServer server, String hash, Path staged) {
        Path target = trackFile(server, hash);
        try {
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            discard(staged);
            throw new UncheckedIOException(exception);
        }
    }

    static CompletableFuture<byte[]> read(MinecraftServer server, String hash, long expectedBytes, BooleanSupplier active) {
        Path source = trackFile(server, hash);
        try {
            return CompletableFuture.supplyAsync(() -> {
                if (!active.getAsBoolean()) {
                    throw new java.util.concurrent.CancellationException();
                }
                try {
                    if (expectedBytes <= 0L || expectedBytes > MusicLimits.HARD_MAX_TRACK_BYTES
                            || Files.size(source) != expectedBytes) {
                        throw new IOException("Unexpected music track size");
                    }
                    byte[] data;
                    try (var input = Files.newInputStream(source)) {
                        data = input.readNBytes((int) expectedBytes + 1);
                    }
                    if (data.length != expectedBytes || !ArchiveHashing.sha256(data).equals(hash)) {
                        throw new IOException("Music track checksum or size mismatch");
                    }
                    return data;
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }, IO_EXECUTOR);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    static void discard(Path temporary) {
        if (temporary == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException exception) {
                com.geydev.kalfactions.KalFactions.LOGGER.warn("Failed to remove staged music file {}", temporary, exception);
            }
        }, Util.ioPool());
    }

    public static void delete(MinecraftServer server, String hash) {
        if (!ArchiveHashing.isSha256(hash)) {
            return;
        }
        Path target = trackFile(server, hash);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            com.geydev.kalfactions.KalFactions.LOGGER.warn("Failed to delete music track {}", hash, exception);
        }
    }

    private MusicStorage() {
    }
}
