package com.geydev.kalfactions.sculk;

import com.geydev.kalfactions.KalFactions;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class NaturalSculkCleaner {
    public static final TagKey<Block> NATURAL_SCULK = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "natural_sculk")
    );
    private static final int BLOCK_BUDGET_PER_TICK = 8192;
    private static final int JOB_VISIT_BUDGET_PER_TICK = 256;
    private static final Map<ResourceKey<Level>, LinkedHashMap<Long, ChunkJob>> JOBS = new LinkedHashMap<>();

    public static void enqueue(ServerLevel level, LevelChunk chunk) {
        LinkedHashMap<Long, ChunkJob> jobs = JOBS.computeIfAbsent(level.dimension(), ignored -> new LinkedHashMap<>());
        long key = chunk.getPos().toLong();
        if (jobs.containsKey(key)) {
            return;
        }
        jobs.put(key, new ChunkJob(chunk.getPos()));
    }

    public static void finishBeforeUnload(ServerLevel level, LevelChunk chunk) {
        LinkedHashMap<Long, ChunkJob> jobs = JOBS.get(level.dimension());
        if (jobs == null) {
            return;
        }
        ChunkJob job = jobs.remove(chunk.getPos().toLong());
        if (job != null) {
            cleanChunk(level, chunk, Integer.MAX_VALUE, job);
        }
        if (jobs.isEmpty()) {
            JOBS.remove(level.dimension());
        }
    }

    public static void tick(Iterable<ServerLevel> levels) {
        int remaining = BLOCK_BUDGET_PER_TICK;
        int remainingVisits = JOB_VISIT_BUDGET_PER_TICK;
        for (ServerLevel level : levels) {
            LinkedHashMap<Long, ChunkJob> jobs = JOBS.get(level.dimension());
            if (jobs == null || jobs.isEmpty()) {
                continue;
            }
            int visits = Math.min(jobs.size(), remainingVisits);
            while (visits-- > 0 && remaining > 0) {
                Iterator<Map.Entry<Long, ChunkJob>> iterator = jobs.entrySet().iterator();
                Map.Entry<Long, ChunkJob> entry = iterator.next();
                long key = entry.getKey();
                ChunkJob job = entry.getValue();
                iterator.remove();
                remainingVisits--;
                LevelChunk chunk = level.getChunkSource().getChunkNow(job.pos.x, job.pos.z);
                if (chunk == null) {
                    jobs.put(key, job);
                    continue;
                }
                int used = cleanChunk(level, chunk, remaining, job);
                remaining -= used;
                if (!job.complete(chunk)) {
                    jobs.put(key, job);
                }
            }
            if (jobs.isEmpty()) {
                JOBS.remove(level.dimension());
            }
            if (remaining <= 0 || remainingVisits <= 0) {
                return;
            }
        }
    }

    public static void clear() {
        JOBS.clear();
    }

    static int cleanChunk(ServerLevel level, LevelChunk chunk, int budget, ChunkJob job) {
        int used = 0;
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (job.sectionIndex < sections.length && used < budget) {
            LevelChunkSection section = sections[job.sectionIndex];
            if (job.localIndex == 0 && !section.maybeHas(state -> state.is(NATURAL_SCULK))) {
                job.sectionIndex++;
                continue;
            }
            int sectionY = chunk.getSectionYFromSectionIndex(job.sectionIndex);
            while (job.localIndex < 4096 && used < budget) {
                int index = job.localIndex++;
                int localX = index & 15;
                int localZ = index >> 4 & 15;
                int localY = index >> 8 & 15;
                pos.set(
                        chunk.getPos().getMinBlockX() + localX,
                        sectionY * 16 + localY,
                        chunk.getPos().getMinBlockZ() + localZ
                );
                var state = chunk.getBlockState(pos);
                if (state.is(NATURAL_SCULK)) {
                    level.setBlock(pos, state.is(Blocks.SCULK_VEIN) ? Blocks.AIR.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState(), 3);
                }
                used++;
            }
            if (job.localIndex == 4096) {
                job.localIndex = 0;
                job.sectionIndex++;
            }
        }
        return used;
    }

    static final class ChunkJob {
        private final ChunkPos pos;
        private int sectionIndex;
        private int localIndex;

        private ChunkJob(ChunkPos pos) {
            this.pos = pos;
        }

        private boolean complete(LevelChunk chunk) {
            return sectionIndex >= chunk.getSections().length;
        }
    }

    private NaturalSculkCleaner() {
    }
}
