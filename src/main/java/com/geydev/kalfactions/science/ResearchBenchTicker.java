package com.geydev.kalfactions.science;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.ResearchBenchBlockEntity;
import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ResearchBenchTicker {
    private static final Map<ResourceKey<Level>, Map<Long, Map<BlockPos, ResearchBenchBlockEntity>>> INDEX =
            new HashMap<>();

    private static int ticks;

    public static synchronized void index(ServerLevel level, ResearchBenchBlockEntity bench) {
        BlockPos pos = bench.getBlockPos();
        INDEX.computeIfAbsent(level.dimension(), dimension -> new HashMap<>())
                .computeIfAbsent(ChunkPos.asLong(pos), chunk -> new LinkedHashMap<>())
                .put(pos.immutable(), bench);
    }

    public static synchronized void forget(ResearchBenchBlockEntity bench) {
        if (!(bench.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<Long, Map<BlockPos, ResearchBenchBlockEntity>> chunks = INDEX.get(level.dimension());
        if (chunks == null) {
            return;
        }
        BlockPos pos = bench.getBlockPos();
        long chunk = ChunkPos.asLong(pos);
        Map<BlockPos, ResearchBenchBlockEntity> benches = chunks.get(chunk);
        if (benches == null) {
            return;
        }
        benches.remove(pos, bench);
        if (benches.isEmpty()) {
            chunks.remove(chunk);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < Math.max(1, ModConfigSpec.RESEARCH_BENCH_CHECK_INTERVAL_TICKS.getAsInt())) {
            return;
        }
        ticks = 0;
        runChecks(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
    }

    public static synchronized void clear() {
        INDEX.clear();
        ticks = 0;
    }

    private static synchronized void runChecks(MinecraftServer server) {
        Iterator<Map.Entry<ResourceKey<Level>, Map<Long, Map<BlockPos, ResearchBenchBlockEntity>>>> dimensions =
                INDEX.entrySet().iterator();
        while (dimensions.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<Long, Map<BlockPos, ResearchBenchBlockEntity>>> dimension =
                    dimensions.next();
            ServerLevel level = server.getLevel(dimension.getKey());
            if (level == null) {
                dimensions.remove();
                continue;
            }
            runChecks(level, dimension.getValue());
            if (dimension.getValue().isEmpty()) {
                dimensions.remove();
            }
        }
    }

    private static void runChecks(ServerLevel level, Map<Long, Map<BlockPos, ResearchBenchBlockEntity>> chunks) {
        Iterator<Map.Entry<Long, Map<BlockPos, ResearchBenchBlockEntity>>> chunkEntries = chunks.entrySet().iterator();
        while (chunkEntries.hasNext()) {
            Map<BlockPos, ResearchBenchBlockEntity> benches = chunkEntries.next().getValue();
            Iterator<ResearchBenchBlockEntity> iterator = benches.values().iterator();
            while (iterator.hasNext()) {
                ResearchBenchBlockEntity bench = iterator.next();
                if (bench.isRemoved() || !level.hasChunkAt(bench.getBlockPos())) {
                    iterator.remove();
                    continue;
                }
                bench.runCheck(level);
            }
            if (benches.isEmpty()) {
                chunkEntries.remove();
            }
        }
    }

    private ResearchBenchTicker() {
    }
}
