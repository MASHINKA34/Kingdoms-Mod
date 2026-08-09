package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.integration.xaero.archive.XaeroRegionCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class ScoutJob {
    private final ScoutOrder order;
    private final ServerLevel level;
    private final ScoutTerrainSampler sampler;
    private final Map<Long, XaeroRegionCodec.RegionBuilder> regions = new LinkedHashMap<>();
    private int cursor;

    public ScoutJob(ScoutOrder order, ServerLevel level) {
        this.order = order;
        this.level = level;
        this.sampler = new ScoutTerrainSampler(level);
    }

    public ScoutOrder order() {
        return order;
    }

    public boolean isDone() {
        return cursor >= order.chunkCount();
    }

    public int tick(int budget) {
        int total = order.chunkCount();
        int processed = 0;
        while (cursor < total && processed < budget) {
            int chunkX = order.minChunkX() + cursor % order.sizeChunks();
            int chunkZ = order.minChunkZ() + cursor / order.sizeChunks();
            capture(chunkX, chunkZ);
            cursor++;
            processed++;
        }
        order.setCursor(cursor);
        return processed;
    }

    private void capture(int chunkX, int chunkZ) {
        ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        if (chunk == null) {
            return;
        }
        List<XaeroRegionCodec.SurfacePixel> pixels = sampler.sample(chunk);
        int regionX = Math.floorDiv(chunkX, XaeroRegionCodec.CHUNKS_PER_REGION);
        int regionZ = Math.floorDiv(chunkZ, XaeroRegionCodec.CHUNKS_PER_REGION);
        regions.computeIfAbsent(ChunkPos.asLong(regionX, regionZ), ignored -> new XaeroRegionCodec.RegionBuilder())
                .tile(chunkX, chunkZ, pixels);
    }

    public List<String> writeStaging(Path stagingRoot) throws IOException {
        Files.createDirectories(stagingRoot);
        List<String> written = new ArrayList<>(regions.size());
        for (Map.Entry<Long, XaeroRegionCodec.RegionBuilder> entry : regions.entrySet()) {
            XaeroRegionCodec.RegionBuilder builder = entry.getValue();
            if (builder.isEmpty()) {
                continue;
            }
            ChunkPos regionPos = new ChunkPos(entry.getKey());
            String name = regionPos.x + "_" + regionPos.z + ".zip";
            builder.writeTo(stagingRoot.resolve(name));
            written.add(name);
        }
        return written;
    }
}
