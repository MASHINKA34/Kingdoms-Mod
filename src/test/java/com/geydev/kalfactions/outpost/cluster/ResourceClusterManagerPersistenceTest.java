package com.geydev.kalfactions.outpost.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
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
}
