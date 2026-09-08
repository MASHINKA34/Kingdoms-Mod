package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.integration.xaero.archive.XaeroRegionCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class ScoutJob implements AutoCloseable {
    private static final TicketType<UUID> SCOUT_TICKET = TicketType.create("kingdoms_scout", UUID::compareTo);
    private final ScoutOrder order;
    private final ServerLevel level;
    private final ScoutTerrainSampler sampler;
    private final Map<Long, XaeroRegionCodec.RegionBuilder> regions = new LinkedHashMap<>();
    private int cursor;
    private ChunkPos pendingChunk;
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private boolean closed;

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
        if (closed || isDone() || budget <= 0) {
            return 0;
        }
        if (pending == null) {
            int chunkX = order.minChunkX() + cursor % order.sizeChunks();
            int chunkZ = order.minChunkZ() + cursor / order.sizeChunks();
            pendingChunk = new ChunkPos(chunkX, chunkZ);
            var chunkSource = level.getChunkSource();
            chunkSource.addRegionTicket(SCOUT_TICKET, pendingChunk, 0, order.id());
            pending = CompletableFuture.supplyAsync(() -> chunkSource.getChunkFuture(chunkX, chunkZ, ChunkStatus.FULL, true))
                    .thenCompose(future -> future);
            return 1;
        }
        if (!pending.isDone()) {
            return 0;
        }
        try {
            ChunkAccess chunk = pending.join().orElse(null);
            if (chunk == null) {
                throw new IllegalStateException("Scout chunk could not be loaded: " + pendingChunk);
            }
            capture(chunk);
            cursor++;
            order.setCursor(cursor);
        } finally {
            releaseTicket();
        }
        return 1;
    }

    private void capture(ChunkAccess chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        List<XaeroRegionCodec.SurfacePixel> pixels = sampler.sample(chunk);
        int regionX = Math.floorDiv(chunkX, XaeroRegionCodec.CHUNKS_PER_REGION);
        int regionZ = Math.floorDiv(chunkZ, XaeroRegionCodec.CHUNKS_PER_REGION);
        regions.computeIfAbsent(ChunkPos.asLong(regionX, regionZ), ignored -> new XaeroRegionCodec.RegionBuilder())
                .tile(chunkX, chunkZ, pixels);
    }

    public List<String> writeStaging(Path stagingRoot) throws IOException {
        if (!isDone()) {
            throw new IllegalStateException("Cannot stage an unfinished scout survey");
        }
        Files.createDirectories(stagingRoot);
        List<String> written = new ArrayList<>(regions.size());
        for (Map.Entry<Long, XaeroRegionCodec.RegionBuilder> entry : regions.entrySet()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException("Scout staging interrupted");
            }
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

    private void releaseTicket() {
        if (pendingChunk != null) {
            level.getChunkSource().removeRegionTicket(SCOUT_TICKET, pendingChunk, 0, order.id());
            pendingChunk = null;
            pending = null;
        }
    }

    @Override
    public void close() {
        closed = true;
        releaseTicket();
    }
}
