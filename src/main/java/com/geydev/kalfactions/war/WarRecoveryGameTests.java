package com.geydev.kalfactions.war;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarRecoveryGameTests {
    @GameTest(template = "empty", batch = "war_recovery", timeoutTicks = 400)
    public static void completedRollbackIsOnDiskAndOldMetadataStillHasItsSnapshot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ClaimKey key = ClaimKey.of(level, pos);
        level.setBlockAndUpdate(pos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        WarChunkSnapshot snapshot = WarChunkSnapshot.capture(level, key.chunk(), level.registryAccess());
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        War war = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), WarType.DEFAULT,
                "recovery", War.State.ENDING, 0L);
        war.putSnapshot(key, snapshot);
        WarManager original = WarManager.get(level);
        WarSnapshotStore.write(level.getServer(), war.id(), key, snapshot);
        CompoundTag metadata = new CompoundTag();
        ListTag wars = new ListTag();
        wars.add(war.save());
        metadata.put("wars", wars);
        WarManager manager = WarManager.FACTORY.deserializer().apply(metadata, level.registryAccess());
        var storage = level.getServer().overworld().getDataStorage();
        storage.set(WarManager.DATA_NAME, manager);
        WarManager.get(level);
        manager.tick(level.getServer(), 1, 0L);
        helper.assertTrue(manager.warForFaction(war.attackerFactionId()).orElseThrow().hasSnapshot(key),
                "snapshot stays referenced while the chunk write is in progress");
        var read = new AtomicReference<CompletableFuture<java.util.Optional<CompoundTag>>>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    manager.tick(level.getServer(), 1, 0L);
                    helper.assertTrue(manager.warForFaction(war.attackerFactionId()).isEmpty(),
                            "rollback waits for a successful chunk write");
                })
                .thenExecute(() -> read.set(level.getChunkSource().chunkMap.read(key.chunk())))
                .thenWaitUntil(() -> helper.assertTrue(read.get().isDone(), "the chunk was read back from disk"))
                .thenExecute(() -> {
                    try {
                        CompoundTag savedChunk = read.get().join().orElseThrow();
                        var info = new RegionStorageInfo("war-recovery-test", level.dimension(), "chunk");
                        var loadedChunk = ChunkSerializer.read(level, level.getPoiManager(), info, key.chunk(), savedChunk);
                        helper.assertTrue(loadedChunk.getBlockState(pos).is(Blocks.DIAMOND_BLOCK),
                                "the restored block exists in the persisted chunk");
                        Map<ClaimKey, WarChunkSnapshot> retained = WarSnapshotStore.readAll(level.getServer(), war.id(), java.util.Set.of(key));
                        helper.assertTrue(retained.containsKey(key), "snapshot files survive completion until metadata is safely pruned");
                        WarManager restarted = WarManager.FACTORY.deserializer().apply(metadata, level.registryAccess());
                        storage.set(WarManager.DATA_NAME, restarted);
                        War restoredWar = WarManager.get(level).warForFaction(war.attackerFactionId()).orElseThrow();
                        helper.assertTrue(restoredWar.snapshot(key) != null && restoredWar.pendingRollback().contains(key),
                                "restarting with older metadata can retry the same rollback");
                    } finally {
                        storage.set(WarManager.DATA_NAME, original);
                        WarSnapshotStore.deleteWar(level.getServer(), war.id());
                    }
                })
                .thenSucceed();
    }

    private WarRecoveryGameTests() {
    }
}
