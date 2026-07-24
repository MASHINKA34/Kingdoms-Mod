package com.geydev.kalfactions.outpost.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
        CompoundTag cluster = new CompoundTag();
        cluster.putUUID("id", UUID.randomUUID());
        cluster.put("basePos", NbtUtils.writeBlockPos(clusterPos));
        cluster.putString("type", ResourceClusterType.SCIENCE.id());
        cluster.putInt("richness", 2);
        cluster.putUUID("itemDisplay", UUID.randomUUID());
        cluster.putUUID("textDisplay", UUID.randomUUID());
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
}
