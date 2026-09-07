package com.geydev.kalfactions.war;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

final class WarChunkPersistence {
    static CompletableFuture<Void> restore(MinecraftServer server, ClaimKey key, WarChunkSnapshot snapshot) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null) {
            throw new IllegalStateException("Rollback dimension is unavailable: " + key.dimension().location());
        }
        if (snapshot == null) {
            throw new IllegalStateException("Rollback snapshot is unavailable: " + key);
        }
        snapshot.restore(level, key.chunk(), level.registryAccess());
        LevelChunk chunk = level.getChunk(key.x(), key.z());
        var chunkMap = level.getChunkSource().chunkMap;
        level.getPoiManager().flush(key.chunk());
        CompoundTag data = ChunkSerializer.write(level, chunk);
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, data));
        return chunkMap.write(key.chunk(), data);
    }

    private WarChunkPersistence() {
    }
}
