package com.geydev.kalfactions.worldmap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

public final class WorldMapStorage {
    private static final String DIR = "kingdoms";
    private static final String IMAGE = "worldmap.png";
    private static final String META = "worldmap.properties";

    public record Meta(
            int centerX,
            int centerZ,
            int regionBlocks,
            int resolution,
            long version,
            ResourceLocation dimension
    ) {
    }

    private WorldMapStorage() {
    }

    public static Optional<Meta> meta(MinecraftServer server) {
        return readMeta(server).map(properties -> new Meta(
                parseInt(properties, "centerX", 0),
                parseInt(properties, "centerZ", 0),
                parseInt(properties, "regionBlocks", 0),
                parseInt(properties, "resolution", 0),
                parseLong(properties, "renderedAt", 0L),
                parseDimension(properties)
        ));
    }

    private static ResourceLocation parseDimension(Properties properties) {
        String raw = properties.getProperty("dimension");
        ResourceLocation parsed = raw == null ? null : ResourceLocation.tryParse(raw);
        return parsed == null ? Level.OVERWORLD.location() : parsed;
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(Properties properties, String key, long fallback) {
        try {
            return Long.parseLong(properties.getProperty(key));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR);
    }

    public static Path imagePath(MinecraftServer server) {
        return directory(server).resolve(IMAGE);
    }

    public static Path metaPath(MinecraftServer server) {
        return directory(server).resolve(META);
    }

    public static boolean exists(MinecraftServer server) {
        return Files.isRegularFile(imagePath(server));
    }

    public static byte[] readImageBytes(MinecraftServer server) throws IOException {
        return Files.readAllBytes(imagePath(server));
    }

    public static Optional<Properties> readMeta(MinecraftServer server) {
        Path path = metaPath(server);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            return Optional.of(properties);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static void save(
            MinecraftServer server,
            BufferedImage image,
            int centerX,
            int centerZ,
            int regionBlocks,
            int resolution,
            ResourceLocation dimension
    ) throws IOException {
        Path directory = directory(server);
        Files.createDirectories(directory);
        ImageIO.write(image, "PNG", imagePath(server).toFile());
        Properties properties = new Properties();
        properties.setProperty("centerX", Integer.toString(centerX));
        properties.setProperty("centerZ", Integer.toString(centerZ));
        properties.setProperty("regionBlocks", Integer.toString(regionBlocks));
        properties.setProperty("resolution", Integer.toString(resolution));
        properties.setProperty("dimension", dimension.toString());
        properties.setProperty("renderedAt", Long.toString(System.currentTimeMillis()));
        try (Writer writer = Files.newBufferedWriter(metaPath(server))) {
            properties.store(writer, "kingdoms world map");
        }
    }
}
