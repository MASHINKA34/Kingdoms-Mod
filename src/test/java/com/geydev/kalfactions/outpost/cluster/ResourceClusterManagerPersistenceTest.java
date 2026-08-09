package com.geydev.kalfactions.outpost.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class ResourceClusterManagerPersistenceTest {
    @Test
    void pendingChunkQueueSurvivesSavedDataRoundTrip() {
        ResourceClusterManager manager = new ResourceClusterManager();
        manager.queue(new ChunkPos(17, -23), 400L);

        CompoundTag saved = manager.save(new CompoundTag(), null);
        ResourceClusterManager loaded = ResourceClusterManager.load(saved, null);

        assertEquals(1, loaded.pendingChunkCount());
    }

    @Test
    void oneClusterOneDrillBindingSurvivesSavedDataRoundTrip() {
        BlockPos clusterPos = new BlockPos(40, 70, -24);
        ChunkPos clusterChunk = new ChunkPos(clusterPos);
        CompoundTag cluster = clusterTag(clusterPos);
        ListTag clusters = new ListTag();
        clusters.add(cluster);
        CompoundTag initial = new CompoundTag();
        initial.put("clusters", clusters);
        ResourceClusterManager manager = ResourceClusterManager.load(initial, null);
        BlockPos drill = new BlockPos(42, 71, -22);
        assertTrue(manager.bindDrill(clusterChunk, drill));

        CompoundTag saved = manager.save(new CompoundTag(), null);
        ResourceClusterManager loaded = ResourceClusterManager.load(saved, null);

        assertTrue(loaded.isBoundDrill(clusterChunk, drill));
        assertTrue(!loaded.bindDrill(clusterChunk, new BlockPos(43, 71, -22)));
    }

    @Test
    void reserveStateAndGenerationSurviveSavedDataRoundTrip() {
        CompoundTag cluster = clusterTag(new BlockPos(40, 70, -24));
        cluster.putInt("spent", 175);
        cluster.putLong("firstExtraction", 1_700_000_000_000L);
        cluster.putBoolean("exhausted", true);
        ListTag clusters = new ListTag();
        clusters.add(cluster);
        CompoundTag initial = new CompoundTag();
        initial.put("clusters", clusters);
        initial.putInt("generation", 3);

        CompoundTag saved = ResourceClusterManager.load(initial, null).save(new CompoundTag(), null);
        CompoundTag reloaded = ResourceClusterManager.load(saved, null).save(new CompoundTag(), null);
        CompoundTag persisted = reloaded.getList("clusters", Tag.TAG_COMPOUND).getCompound(0);

        assertEquals(3, reloaded.getInt("generation"));
        assertEquals(175, persisted.getInt("spent"));
        assertEquals(1_700_000_000_000L, persisted.getLong("firstExtraction"));
        assertTrue(persisted.getBoolean("exhausted"));
    }

    @Test
    void migratedClusterWithoutReserveTagsStartsFullWithoutTimer() {
        ListTag clusters = new ListTag();
        clusters.add(clusterTag(new BlockPos(40, 70, -24)));
        CompoundTag initial = new CompoundTag();
        initial.put("clusters", clusters);

        CompoundTag saved = ResourceClusterManager.load(initial, null).save(new CompoundTag(), null);
        CompoundTag persisted = saved.getList("clusters", Tag.TAG_COMPOUND).getCompound(0);

        assertEquals(0, saved.getInt("generation"));
        assertEquals(0, persisted.getInt("spent"));
        assertEquals(0L, persisted.getLong("firstExtraction"));
        assertFalse(persisted.getBoolean("exhausted"));
    }

    @Test
    void staleClustersFromRegenerationSurviveSavedDataRoundTrip() {
        BlockPos stalePos = new BlockPos(40, 70, -24);
        ListTag clusters = new ListTag();
        clusters.add(clusterTag(stalePos));
        CompoundTag initial = new CompoundTag();
        initial.put("clusters", clusters);
        ResourceClusterManager manager = ResourceClusterManager.load(initial, null);
        assertEquals(1, manager.beginRegeneration());

        CompoundTag saved = manager.save(new CompoundTag(), null);
        CompoundTag reloaded = ResourceClusterManager.load(saved, null).save(new CompoundTag(), null);
        ListTag stale = reloaded.getList("staleClusters", Tag.TAG_COMPOUND);

        assertEquals(1, reloaded.getInt("generation"));
        assertEquals(0, reloaded.getList("clusters", Tag.TAG_COMPOUND).size());
        assertEquals(1, stale.size());
        assertEquals(new ChunkPos(stalePos).toLong(), stale.getCompound(0).getLong("chunk"));
    }

    private static CompoundTag clusterTag(BlockPos pos) {
        CompoundTag cluster = new CompoundTag();
        cluster.putUUID("id", UUID.randomUUID());
        cluster.put("basePos", NbtUtils.writeBlockPos(pos));
        cluster.putString("type", ResourceClusterType.SCIENCE.id());
        cluster.putInt("richness", 2);
        cluster.putUUID("itemDisplay", UUID.randomUUID());
        cluster.putUUID("textDisplay", UUID.randomUUID());
        return cluster;
    }
}
