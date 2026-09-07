package com.geydev.kalfactions.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

final class SavedDataInventory {
    static final Path SOURCE_ROOT = Path.of("src/main/java/com/geydev/kalfactions");

    static final List<String> CLASS_NAMES = scan();

    private static List<String> scan() {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(SavedDataInventory::declaresSavedData)
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean declaresSavedData(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains("extends SavedData {");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private SavedDataInventory() {
    }
}
