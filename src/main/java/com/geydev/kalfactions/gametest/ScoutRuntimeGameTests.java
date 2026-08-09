package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveManifest;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveStore;
import com.geydev.kalfactions.integration.xaero.archive.XaeroRegionCodec;
import com.geydev.kalfactions.scout.ScoutJob;
import com.geydev.kalfactions.scout.ScoutOrder;
import com.geydev.kalfactions.scout.ScoutPackage;
import com.geydev.kalfactions.scout.ScoutTerrainSampler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScoutRuntimeGameTests {
    private static final int SIZE = 3;
    private static final int FAR_CHUNK = 30_000;

    @GameTest(template = "empty", batch = "scout_runtime", timeoutTicks = 600)
    public static void surveysUngeneratedTerrainIntoTheFactionArchive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int centreChunkX = FAR_CHUNK;
        int centreChunkZ = FAR_CHUNK + level.random.nextInt(64);
        if (level.getChunkSource().getChunkNow(centreChunkX, centreChunkZ) != null) {
            throw new GameTestAssertException("The scouting target was already loaded before the survey");
        }

        ScoutOrder order = new ScoutOrder(
                UUID.randomUUID(),
                ScoutPackage.SMALL,
                level.dimension(),
                centreChunkX,
                centreChunkZ,
                SIZE,
                System.currentTimeMillis(),
                0L,
                0L,
                null
        );
        ScoutJob job = new ScoutJob(order, level);
        int guard = 0;
        while (!job.isDone() && guard++ < SIZE * SIZE * 4) {
            job.tick(1);
        }
        if (!job.isDone()) {
            throw new GameTestAssertException("The scout job never finished its budgeted survey");
        }

        UUID factionId = UUID.randomUUID();
        Path staging = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("kingdoms").resolve("scout_gametest").resolve(order.id().toString());
        try {
            List<String> names = job.writeStaging(staging);
            if (names.isEmpty()) {
                throw new GameTestAssertException("The scout produced no Xaero regions");
            }
            int tiles = 0;
            for (String name : names) {
                XaeroRegionCodec.RegionStats stats = XaeroRegionCodec.inspect(staging.resolve(name));
                tiles += stats.tileCount();
                if (stats.uncompressedSize() < (long) stats.tileCount() * XaeroRegionCodec.PIXELS_PER_TILE * 4L) {
                    throw new GameTestAssertException("Xaero region " + name + " is smaller than its pixel payload");
                }
            }
            if (tiles != SIZE * SIZE) {
                throw new GameTestAssertException("Expected " + SIZE * SIZE + " Xaero tiles but wrote " + tiles);
            }

            XaeroArchiveStore.ArchiveLocation location =
                    XaeroArchiveStore.location(level.getServer(), factionId, level.dimension().location());
            List<XaeroArchiveStore.IncomingRegion> incoming = new ArrayList<>(names.size());
            for (String name : names) {
                incoming.add(new XaeroArchiveStore.IncomingRegion(name, staging.resolve(name)));
            }
            XaeroArchiveManifest manifest = XaeroArchiveStore.merge(location, incoming);
            if (manifest.regions().size() != names.size()) {
                throw new GameTestAssertException("The faction archive did not take every scouted region");
            }
            deleteRecursively(location.root());
        } catch (IOException exception) {
            throw new GameTestAssertException("Scout archive round trip failed: " + exception);
        } finally {
            deleteRecursively(staging);
        }

        ChunkAccess chunk = level.getChunk(centreChunkX, centreChunkZ, ChunkStatus.FULL, true);
        List<XaeroRegionCodec.SurfacePixel> pixels = new ScoutTerrainSampler(level).sample(chunk);
        if (pixels.size() != XaeroRegionCodec.PIXELS_PER_TILE) {
            throw new GameTestAssertException("A Xaero tile must hold 256 pixels, got " + pixels.size());
        }
        long described = pixels.stream()
                .filter(pixel -> pixel.biome() != null && pixel.height() > level.getMinBuildHeight())
                .count();
        if (described != XaeroRegionCodec.PIXELS_PER_TILE) {
            throw new GameTestAssertException(
                    "Only " + described + " of 256 scouted pixels carry a biome and terrain height"
            );
        }
        helper.succeed();
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    KalFactions.LOGGER.debug("Could not delete scout game test file {}", path);
                }
            });
        } catch (IOException ignored) {
            KalFactions.LOGGER.debug("Could not delete scout game test directory {}", root);
        }
    }

    private ScoutRuntimeGameTests() {
    }
}
