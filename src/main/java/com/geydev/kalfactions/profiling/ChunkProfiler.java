package com.geydev.kalfactions.profiling;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.tax.TaxMath;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ChunkProfiler {
    private static final double EMA_DROP_THRESHOLD = 0.0001D;

    private static volatile boolean sampling;
    private static long tickIndex;
    private static int lastSampleInterval = 4;

    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> TICK_NANOS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long2DoubleOpenHashMap> EMA_MS = new HashMap<>();

    private static boolean measuring;
    private static Level measuringLevel;
    private static long measuringChunk;
    private static long measuringStart;

    private static ResourceKey<Level> captureDimension;
    private static long captureChunk;
    private static int captureSamplesLeft;
    private static int captureSamplesTotal;
    private static UUID capturePlayer;
    private static final Object2LongOpenHashMap<BlockPos> CAPTURE_NANOS = new Object2LongOpenHashMap<>();

    public static boolean sampling() {
        return sampling;
    }

    public static void onServerTickStart() {
        int interval = Math.max(1, ModConfigSpec.LAGTAX_SAMPLE_INTERVAL_TICKS.getAsInt());
        lastSampleInterval = interval;
        sampling = ++tickIndex % interval == 0L;
        if (sampling) {
            TICK_NANOS.values().forEach(Long2LongOpenHashMap::clear);
        }
    }

    public static void begin(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        measuring = true;
        measuringLevel = level;
        measuringChunk = ChunkPos.asLong(blockEntity.getBlockPos());
        measuringStart = System.nanoTime();
    }

    public static void end(BlockEntity blockEntity) {
        if (!measuring || blockEntity.getLevel() != measuringLevel) {
            return;
        }
        measuring = false;
        long elapsed = System.nanoTime() - measuringStart;
        ResourceKey<Level> dimension = measuringLevel.dimension();
        TICK_NANOS.computeIfAbsent(dimension, ignored -> new Long2LongOpenHashMap())
            .addTo(measuringChunk, elapsed);
        if (captureSamplesLeft > 0 && measuringChunk == captureChunk && dimension.equals(captureDimension)) {
            CAPTURE_NANOS.addTo(blockEntity.getBlockPos().immutable(), elapsed);
        }
    }

    public static SampleResult flush(MinecraftServer server) {
        if (!sampling) {
            return null;
        }
        sampling = false;
        measuring = false;
        double alpha = TaxMath.emaAlpha(lastSampleInterval, ModConfigSpec.LAGTAX_EMA_SECONDS.getAsInt());
        FactionManager manager = FactionManager.get(server);
        Map<UUID, Double> factionLoads = new HashMap<>();

        for (Map.Entry<ResourceKey<Level>, Long2LongOpenHashMap> dimensionEntry : TICK_NANOS.entrySet()) {
            ResourceKey<Level> dimension = dimensionEntry.getKey();
            Long2LongOpenHashMap nanosMap = dimensionEntry.getValue();
            Long2DoubleOpenHashMap emaMap = EMA_MS.computeIfAbsent(dimension, ignored -> new Long2DoubleOpenHashMap());

            var emaIterator = emaMap.long2DoubleEntrySet().fastIterator();
            while (emaIterator.hasNext()) {
                Long2DoubleMap.Entry entry = emaIterator.next();
                if (nanosMap.containsKey(entry.getLongKey())) {
                    continue;
                }
                double decayed = entry.getDoubleValue() * (1.0D - alpha);
                if (decayed < EMA_DROP_THRESHOLD) {
                    emaIterator.remove();
                } else {
                    entry.setValue(decayed);
                }
            }

            for (Long2LongMap.Entry entry : nanosMap.long2LongEntrySet()) {
                long packedChunk = entry.getLongKey();
                double ms = entry.getLongValue() / 1_000_000.0D;
                double previous = emaMap.get(packedChunk);
                emaMap.put(packedChunk, previous + alpha * (ms - previous));
                UUID factionId = manager
                    .getFactionIdAt(new ClaimKey(dimension, new ChunkPos(packedChunk)))
                    .orElse(null);
                if (factionId != null) {
                    factionLoads.merge(factionId, ms, Double::sum);
                }
            }
        }

        CaptureResult capture = advanceCapture(server);
        return new SampleResult(factionLoads, lastSampleInterval, capture);
    }

    public static double chunkLoadMs(ResourceKey<Level> dimension, long packedChunk) {
        Long2DoubleOpenHashMap emaMap = EMA_MS.get(dimension);
        return emaMap == null ? 0.0D : emaMap.get(packedChunk);
    }

    public static List<ChunkSample> allChunks() {
        List<ChunkSample> samples = new ArrayList<>();
        for (Map.Entry<ResourceKey<Level>, Long2DoubleOpenHashMap> dimensionEntry : EMA_MS.entrySet()) {
            for (Long2DoubleMap.Entry entry : dimensionEntry.getValue().long2DoubleEntrySet()) {
                samples.add(new ChunkSample(dimensionEntry.getKey(), entry.getLongKey(), entry.getDoubleValue()));
            }
        }
        samples.sort(Comparator.comparingDouble(ChunkSample::loadMs).reversed());
        return samples;
    }

    public static double factionLoadMs(MinecraftServer server, UUID factionId) {
        FactionManager manager = FactionManager.get(server);
        double total = 0.0D;
        for (Map.Entry<ResourceKey<Level>, Long2DoubleOpenHashMap> dimensionEntry : EMA_MS.entrySet()) {
            for (Long2DoubleMap.Entry entry : dimensionEntry.getValue().long2DoubleEntrySet()) {
                UUID owner = manager
                    .getFactionIdAt(new ClaimKey(dimensionEntry.getKey(), new ChunkPos(entry.getLongKey())))
                    .orElse(null);
                if (factionId.equals(owner)) {
                    total += entry.getDoubleValue();
                }
            }
        }
        return total;
    }

    public static boolean startCapture(ResourceKey<Level> dimension, long packedChunk, int sampleCount, UUID playerId) {
        if (captureSamplesLeft > 0) {
            return false;
        }
        captureDimension = dimension;
        captureChunk = packedChunk;
        captureSamplesTotal = Math.max(1, sampleCount);
        captureSamplesLeft = captureSamplesTotal;
        capturePlayer = playerId;
        CAPTURE_NANOS.clear();
        return true;
    }

    private static CaptureResult advanceCapture(MinecraftServer server) {
        if (captureSamplesLeft <= 0) {
            return null;
        }
        if (--captureSamplesLeft > 0) {
            return null;
        }
        List<BlockEntitySample> entries = new ArrayList<>();
        ServerLevel level = server.getLevel(captureDimension);
        for (Object2LongMap.Entry<BlockPos> entry : CAPTURE_NANOS.object2LongEntrySet()) {
            double ms = entry.getLongValue() / 1_000_000.0D / captureSamplesTotal;
            String blockId = "";
            if (level != null && level.isLoaded(entry.getKey())) {
                blockId = level.getBlockState(entry.getKey()).getBlock().builtInRegistryHolder().key().location().toString();
            }
            entries.add(new BlockEntitySample(entry.getKey(), blockId, ms));
        }
        entries.sort(Comparator.comparingDouble(BlockEntitySample::loadMs).reversed());
        CaptureResult result = new CaptureResult(captureDimension, captureChunk, capturePlayer, entries);
        CAPTURE_NANOS.clear();
        captureDimension = null;
        capturePlayer = null;
        return result;
    }

    public record SampleResult(Map<UUID, Double> factionLoads, int ticksCovered, CaptureResult capture) {
    }

    public record CaptureResult(
        ResourceKey<Level> dimension,
        long packedChunk,
        UUID playerId,
        List<BlockEntitySample> entries
    ) {
    }

    public record ChunkSample(ResourceKey<Level> dimension, long packedChunk, double loadMs) {
    }

    public record BlockEntitySample(BlockPos pos, String blockId, double loadMs) {
    }

    private ChunkProfiler() {
    }
}
