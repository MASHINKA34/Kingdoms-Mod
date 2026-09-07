package com.geydev.kalfactions.war;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class WarSnapshotStore {
    private static final String DIRECTORY = "kingdoms";
    private static final String WARS = "wars";
    private static final String EXTENSION = ".nbt";

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .resolve(DIRECTORY)
                .resolve(WARS);
    }

    public static void write(MinecraftServer server, UUID warId, ClaimKey key, WarChunkSnapshot snapshot) {
        write(root(server), warId, key, snapshot);
    }

    static void write(Path root, UUID warId, ClaimKey key, WarChunkSnapshot snapshot) {
        Path target = file(root, warId, key);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            NbtIo.writeCompressed(snapshot.save(), temporary);
            move(temporary, target);
        } catch (IOException exception) {
            discard(temporary);
            KalFactions.LOGGER.error("Failed to write war {} snapshot for {}", warId, key, exception);
        }
    }

    public static Map<ClaimKey, WarChunkSnapshot> readAll(MinecraftServer server, UUID warId, Set<ClaimKey> keys) {
        return readAll(root(server), warId, keys);
    }

    static Map<ClaimKey, WarChunkSnapshot> readAll(Path root, UUID warId, Set<ClaimKey> keys) {
        Map<ClaimKey, WarChunkSnapshot> loaded = new LinkedHashMap<>();
        for (ClaimKey key : keys) {
            Path source = file(root, warId, key);
            if (!Files.isRegularFile(source)) {
                KalFactions.LOGGER.warn("War {} snapshot for {} is missing; that chunk will not roll back", warId, key);
                continue;
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
                loaded.put(key, WarChunkSnapshot.load(tag));
            } catch (IOException | RuntimeException exception) {
                KalFactions.LOGGER.error("Failed to read war {} snapshot for {}", warId, key, exception);
            }
        }
        return loaded;
    }

    public static void delete(MinecraftServer server, UUID warId, ClaimKey key) {
        delete(root(server), warId, key);
    }

    static void delete(Path root, UUID warId, ClaimKey key) {
        try {
            Files.deleteIfExists(file(root, warId, key));
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Failed to delete war {} snapshot for {}", warId, key, exception);
        }
    }

    public static void deleteWar(MinecraftServer server, UUID warId) {
        deleteWar(root(server), warId);
    }

    static void deleteWar(Path root, UUID warId) {
        Path folder = warFolder(root, warId);
        if (!Files.exists(folder)) {
            return;
        }
        try (var paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    KalFactions.LOGGER.warn("Failed to delete {}", path, exception);
                }
            });
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Failed to clear the snapshot folder of war {}", warId, exception);
        }
    }

    public static void pruneOrphans(MinecraftServer server, Set<UUID> knownWars) {
        pruneOrphans(root(server), knownWars);
    }

    static void pruneOrphans(Path root, Set<UUID> knownWars) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var paths = Files.list(root)) {
            paths.filter(Files::isDirectory).toList().forEach(folder -> {
                UUID warId;
                try {
                    warId = UUID.fromString(folder.getFileName().toString());
                } catch (IllegalArgumentException exception) {
                    return;
                }
                if (!knownWars.contains(warId)) {
                    deleteWar(root, warId);
                }
            });
        } catch (IOException exception) {
            KalFactions.LOGGER.warn("Failed to scan the war snapshot folder", exception);
        }
    }

    static Path warFolder(Path root, UUID warId) {
        Path folder = root.resolve(warId.toString()).normalize();
        if (!folder.startsWith(root)) {
            throw new IllegalArgumentException("War id escapes the snapshot folder");
        }
        return folder;
    }

    static Path file(Path root, UUID warId, ClaimKey key) {
        Path folder = warFolder(root, warId);
        Path target = folder.resolve(fileName(key)).normalize();
        if (!target.startsWith(folder)) {
            throw new IllegalArgumentException("Claim key escapes the snapshot folder");
        }
        return target;
    }

    static String fileName(ClaimKey key) {
        String dimension = key.dimension().location().toString()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return dimension + "_" + key.x() + "_" + key.z() + EXTENSION;
    }

    private static void move(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void discard(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private WarSnapshotStore() {
    }
}
