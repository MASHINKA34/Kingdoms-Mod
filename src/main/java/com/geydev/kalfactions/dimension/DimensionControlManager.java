package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.storage.LevelResource;

public final class DimensionControlManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "kingdoms_dimension_control.json";
    private static DimensionControlManager instance;

    private final Path file;
    private final State state;
    private final Set<ResourceKey<Level>> wipedThisStartup = new HashSet<>();

    private DimensionControlManager(Path file, State state) {
        this.file = file;
        this.state = state;
    }

    public static synchronized DimensionControlManager get(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).normalize().resolve(FILE_NAME);
        if (instance == null || !instance.file.equals(path)) {
            instance = new DimensionControlManager(path, loadState(path));
        }
        return instance;
    }

    public static boolean isControlled(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) || Level.END.equals(dimension);
    }

    public synchronized boolean isClosed(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherClosed;
        }
        if (Level.END.equals(dimension)) {
            return state.endClosed;
        }
        return false;
    }

    public synchronized boolean setClosed(ResourceKey<Level> dimension, boolean closed) {
        if (Level.NETHER.equals(dimension)) {
            if (state.netherClosed == closed) {
                return false;
            }
            state.netherClosed = closed;
        } else if (Level.END.equals(dimension)) {
            if (state.endClosed == closed) {
                return false;
            }
            state.endClosed = closed;
        } else {
            return false;
        }
        save();
        return true;
    }

    public synchronized boolean isWipePending(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherWipePending;
        }
        if (Level.END.equals(dimension)) {
            return state.endWipePending;
        }
        return false;
    }

    public synchronized boolean setWipePending(ResourceKey<Level> dimension, boolean pending) {
        if (Level.NETHER.equals(dimension)) {
            if (state.netherWipePending == pending) {
                return false;
            }
            state.netherWipePending = pending;
        } else if (Level.END.equals(dimension)) {
            if (state.endWipePending == pending) {
                return false;
            }
            state.endWipePending = pending;
        } else {
            return false;
        }
        save();
        return true;
    }

    public synchronized long wipeGeneration(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return state.netherWipeGen;
        }
        if (Level.END.equals(dimension)) {
            return state.endWipeGen;
        }
        return 0L;
    }

    public synchronized void runPendingWipes(MinecraftServer server) {
        boolean changed = false;
        if (state.netherWipePending && wipeFolder(server, Level.NETHER)) {
            state.netherWipePending = false;
            state.netherWipeGen++;
            wipedThisStartup.add(Level.NETHER);
            changed = true;
        }
        if (state.endWipePending && wipeFolder(server, Level.END)) {
            state.endWipePending = false;
            state.endWipeGen++;
            server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
            wipedThisStartup.add(Level.END);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public synchronized Set<ResourceKey<Level>> consumeWipedThisStartup() {
        Set<ResourceKey<Level>> wiped = Set.copyOf(wipedThisStartup);
        wipedThisStartup.clear();
        return wiped;
    }

    private boolean wipeFolder(MinecraftServer server, ResourceKey<Level> dimension) {
        Path folder = DimensionType.getStorageFolder(
                dimension,
                server.getWorldPath(LevelResource.ROOT).normalize()
        );
        if (!Files.exists(folder)) {
            KalFactions.LOGGER.info("Dimension folder {} is already absent, nothing to wipe", folder);
            return true;
        }
        try {
            deleteRecursively(folder);
            KalFactions.LOGGER.info("Wiped dimension folder {}", folder);
            return true;
        } catch (IOException exception) {
            KalFactions.LOGGER.error("Failed to wipe dimension folder {}, will retry on next startup", folder, exception);
            return false;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static State loadState(Path path) {
        if (!Files.exists(path)) {
            return new State();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            State loaded = GSON.fromJson(json, State.class);
            return loaded == null ? new State() : loaded;
        } catch (IOException | JsonSyntaxException exception) {
            KalFactions.LOGGER.error("Failed to read {}, starting with defaults", path, exception);
            return new State();
        }
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            KalFactions.LOGGER.error("Failed to write {}", file, exception);
        }
    }

    private static final class State {
        private boolean netherClosed;
        private boolean endClosed;
        private boolean netherWipePending;
        private boolean endWipePending;
        private long netherWipeGen;
        private long endWipeGen;
    }
}
