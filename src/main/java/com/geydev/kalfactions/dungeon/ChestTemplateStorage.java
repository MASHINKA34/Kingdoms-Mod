package com.geydev.kalfactions.dungeon;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class ChestTemplateStorage {
    public static final String EXTENSION = ".nbt";

    private static final String DIRECTORY = "kingdoms";
    private static final String TEMPLATES = "chest_templates";
    private static final int MAX_FILE_NAME_LENGTH = 64;
    private static final long MAX_FILE_BYTES = 4L * 1024L * 1024L;

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize()
                .resolve(DIRECTORY)
                .resolve(TEMPLATES);
    }

    public static String fileName(String name) {
        StringBuilder cleaned = new StringBuilder(MAX_FILE_NAME_LENGTH);
        name.codePoints()
                .map(codePoint -> Character.isLetterOrDigit(codePoint) || codePoint == '-' || codePoint == '_'
                        ? codePoint
                        : '_')
                .limit(MAX_FILE_NAME_LENGTH)
                .forEach(cleaned::appendCodePoint);
        String result = cleaned.toString().replaceAll("_+", "_").replaceAll("^_|_$", "");
        return result.isEmpty() ? "template" : result;
    }

    public static Optional<Path> resolve(MinecraftServer server, String requested) {
        String trimmed = requested == null ? "" : requested.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            trimmed = trimmed.substring(0, trimmed.length() - EXTENSION.length());
        }
        String cleaned = fileName(trimmed);
        if (trimmed.isEmpty() || !cleaned.equals(trimmed)) {
            return Optional.empty();
        }
        Path root = root(server);
        Path target = root.resolve(cleaned + EXTENSION).normalize();
        return target.getParent() != null && target.getParent().equals(root)
                ? Optional.of(target)
                : Optional.empty();
    }

    public static Path write(MinecraftServer server, ChestTemplate template, HolderLookup.Provider registries)
            throws IOException {
        Path root = root(server);
        Files.createDirectories(root);
        Path target = root.resolve(fileName(template.name()) + EXTENSION);
        NbtIo.writeCompressed(template.save(registries), target);
        return target;
    }

    public static Optional<ChestTemplate> read(Path source, HolderLookup.Provider registries) throws IOException {
        if (!Files.isRegularFile(source) || Files.size(source) > MAX_FILE_BYTES) {
            return Optional.empty();
        }
        CompoundTag tag = NbtIo.readCompressed(source, NbtAccounter.create(MAX_FILE_BYTES));
        return ChestTemplate.load(tag, registries);
    }

    public static List<String> list(MinecraftServer server) {
        Path root = root(server);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*" + EXTENSION)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                names.add(name.substring(0, name.length() - EXTENSION.length()));
            }
        } catch (IOException ignored) {
            return List.copyOf(names);
        }
        return List.copyOf(names);
    }

    private ChestTemplateStorage() {
    }
}
