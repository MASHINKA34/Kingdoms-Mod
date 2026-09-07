package com.geydev.kalfactions.war;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WarSnapshotPersistenceTest {
    private static final UUID WAR_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_WAR = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DEFENDER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @BeforeAll
    static void bootstrap() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ClaimKey key(int x, int z) {
        return new ClaimKey(Level.OVERWORLD, new ChunkPos(x, z));
    }

    private static War war() {
        return new War(WAR_ID, ATTACKER, DEFENDER, WarType.DEFAULT, "test", War.State.ACTIVE, 0L);
    }

    private static CompoundTag emptySnapshotTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minSection", -4);
        tag.put("sections", new ListTag());
        tag.put("blockEntities", new ListTag());
        return tag;
    }

    @Test
    void savedTagCarriesKeysWithoutChunkData() {
        War war = war();
        war.putSnapshot(key(3, -4), WarChunkSnapshot.load(emptySnapshotTag()));

        ListTag snapshots = war.save().getList("snapshots", Tag.TAG_COMPOUND);

        assertEquals(1, snapshots.size());
        CompoundTag entry = snapshots.getCompound(0);
        assertTrue(entry.contains("key", Tag.TAG_COMPOUND), "the claim key is still persisted");
        assertFalse(entry.contains("data"), "chunk data must not go back into the save file");
    }

    @Test
    void reloadedWarRemembersItsSnapshotKeysAsUnloaded() {
        War war = war();
        war.putSnapshot(key(3, -4), WarChunkSnapshot.load(emptySnapshotTag()));
        war.putSnapshot(key(10, 11), WarChunkSnapshot.load(emptySnapshotTag()));

        War reloaded = War.load(war.save()).orElseThrow();

        assertEquals(2, reloaded.snapshotCount());
        assertFalse(reloaded.snapshotsEmpty());
        assertTrue(reloaded.hasSnapshot(key(3, -4)));
        assertTrue(reloaded.hasSnapshot(key(10, 11)));
        assertFalse(reloaded.hasSnapshot(key(0, 0)));
        assertEquals(Set.of(key(3, -4), key(10, 11)), reloaded.snapshotKeys());
        assertEquals(Set.of(key(3, -4), key(10, 11)), reloaded.unloadedSnapshots());
        assertTrue(reloaded.loadedSnapshots().isEmpty());
    }

    @Test
    void removingAnUnloadedSnapshotStillEmptiesTheWar() {
        War war = war();
        war.putSnapshot(key(1, 1), WarChunkSnapshot.load(emptySnapshotTag()));
        War reloaded = War.load(war.save()).orElseThrow();

        reloaded.removeSnapshot(key(1, 1));

        assertTrue(reloaded.snapshotsEmpty());
        assertEquals(0, reloaded.snapshotCount());
    }

    @Test
    void missingSnapshotFilesRemainPendingAfterHydrationAndReload() {
        War war = war();
        war.putSnapshot(key(1, 1), WarChunkSnapshot.load(emptySnapshotTag()));
        war.putSnapshot(key(2, 2), WarChunkSnapshot.load(emptySnapshotTag()));
        war.setState(War.State.ENDING);
        War reloaded = War.load(war.save()).orElseThrow();
        reloaded.hydrateSnapshots(Map.of(key(1, 1), WarChunkSnapshot.load(emptySnapshotTag())));
        assertEquals(Set.of(key(2, 2)), reloaded.unloadedSnapshots());
        assertEquals(2, reloaded.snapshotCount());
        assertEquals(Set.of(key(1, 1), key(2, 2)), War.load(reloaded.save()).orElseThrow().pendingRollback());
    }

    @Test
    void participantWithdrawalRemembersItsRollbackWhileTheWarContinues() {
        War war = war();
        war.putSnapshot(key(1, 1), WarChunkSnapshot.load(emptySnapshotTag()));
        war.putSnapshot(key(2, 2), WarChunkSnapshot.load(emptySnapshotTag()));
        war.queueRollback(key(1, 1));
        War reloaded = War.load(war.save()).orElseThrow();
        assertEquals(War.State.ACTIVE, reloaded.state());
        assertEquals(Set.of(key(1, 1)), reloaded.pendingRollback());
        reloaded.removeSnapshot(key(1, 1));
        assertTrue(War.load(reloaded.save()).orElseThrow().pendingRollback().isEmpty());
        assertTrue(reloaded.hasSnapshot(key(2, 2)));
    }

    @Test
    void legacyTagWithInlineDataStillLoads() {
        CompoundTag entry = new CompoundTag();
        entry.put("key", key(2, 2).save());
        entry.put("data", emptySnapshotTag());
        ListTag snapshots = new ListTag();
        snapshots.add(entry);
        CompoundTag tag = war().save();
        tag.put("snapshots", snapshots);

        War reloaded = War.load(tag).orElseThrow();

        assertEquals(1, reloaded.snapshotCount());
        assertTrue(reloaded.unloadedSnapshots().isEmpty(), "inline data is loaded, not deferred to a file");
        assertEquals(1, reloaded.loadedSnapshots().size());
    }

    @Test
    void snapshotSurvivesAWriteAndReadRoundTrip(@TempDir Path root) {
        ClaimKey key = key(-7, 12);
        WarSnapshotStore.write(root, WAR_ID, key, WarChunkSnapshot.load(emptySnapshotTag()));

        Map<ClaimKey, WarChunkSnapshot> loaded = WarSnapshotStore.readAll(root, WAR_ID, Set.of(key));

        assertEquals(Set.of(key), loaded.keySet());
        assertTrue(Files.isRegularFile(root.resolve(WAR_ID.toString()).resolve("minecraft_overworld_-7_12.nbt")));
    }

    @Test
    void deletingAWarClearsItsFolder(@TempDir Path root) {
        WarSnapshotStore.write(root, WAR_ID, key(0, 0), WarChunkSnapshot.load(emptySnapshotTag()));
        WarSnapshotStore.write(root, WAR_ID, key(1, 0), WarChunkSnapshot.load(emptySnapshotTag()));

        WarSnapshotStore.deleteWar(root, WAR_ID);

        assertFalse(Files.exists(root.resolve(WAR_ID.toString())));
    }

    @Test
    void missingFilesDoNotFailTheRestOfTheRead(@TempDir Path root) {
        ClaimKey present = key(4, 4);
        WarSnapshotStore.write(root, WAR_ID, present, WarChunkSnapshot.load(emptySnapshotTag()));

        Map<ClaimKey, WarChunkSnapshot> loaded =
                WarSnapshotStore.readAll(root, WAR_ID, Set.of(present, key(5, 5)));

        assertEquals(Set.of(present), loaded.keySet());
    }

    @Test
    void pruneOrphansKeepsOnlyKnownWars(@TempDir Path root) throws Exception {
        WarSnapshotStore.write(root, WAR_ID, key(0, 0), WarChunkSnapshot.load(emptySnapshotTag()));
        WarSnapshotStore.write(root, OTHER_WAR, key(0, 0), WarChunkSnapshot.load(emptySnapshotTag()));
        Files.createDirectories(root.resolve("not-a-uuid"));

        WarSnapshotStore.pruneOrphans(root, Set.of(WAR_ID));

        assertTrue(Files.exists(root.resolve(WAR_ID.toString())));
        assertFalse(Files.exists(root.resolve(OTHER_WAR.toString())));
        assertTrue(Files.exists(root.resolve("not-a-uuid")), "unrelated folders are left alone");
    }

    @Test
    void queuedWritesLandOnDiskOnceFlushed(@TempDir Path root) {
        ClaimKey key = key(2, -3);

        WarSnapshotStore.writeAsync(root, WAR_ID, key, WarChunkSnapshot.load(emptySnapshotTag()));
        WarSnapshotStore.flush();

        assertTrue(Files.isRegularFile(WarSnapshotStore.file(root, WAR_ID, key)));
    }

    @Test
    void queuedWritesAndDeletesKeepTheirOrder(@TempDir Path root) {
        ClaimKey key = key(7, 7);

        WarSnapshotStore.writeAsync(root, WAR_ID, key, WarChunkSnapshot.load(emptySnapshotTag()));
        WarSnapshotStore.deleteAsync(root, WAR_ID, key);
        WarSnapshotStore.flush();

        assertFalse(Files.exists(WarSnapshotStore.file(root, WAR_ID, key)));
    }

    @Test
    void fileNamesCannotEscapeTheWarFolder() {
        assertEquals("minecraft_overworld_3_-4.nbt", WarSnapshotStore.fileName(key(3, -4)));

        ResourceKey<Level> nested = ResourceKey.create(
                Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("kingdoms", "a/b")
        );
        String name = WarSnapshotStore.fileName(new ClaimKey(nested, new ChunkPos(0, 0)));

        assertFalse(name.contains("/"), name);
        assertFalse(name.contains(".."), name);
        assertEquals("kingdoms_a_b_0_0.nbt", name);
    }
}
