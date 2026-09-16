package com.geydev.kalfactions.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DungeonLightingPersistenceTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    @BeforeAll
    static void bootstrap() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aDungeonSavedBeforeLightingLoadsAsSwitchedOff() {
        DungeonManager manager = DungeonManager.load(rootWith(dungeonTag(7, null)), null);

        DungeonManager.DungeonView dungeon = manager.byId(7).orElseThrow();
        assertEquals(0, dungeon.lighting());
        assertEquals("Тест", dungeon.name());
        assertTrue(manager.isDungeon(new ClaimKey(OVERWORLD, 3, -4)));
    }

    @Test
    void theLightingLevelSurvivesASaveAndLoad() {
        DungeonManager manager = DungeonManager.load(rootWith(dungeonTag(7, null)), null);

        assertEquals(DungeonManager.Reason.OK, manager.setLighting(7, 2));
        assertEquals(2, manager.byId(7).orElseThrow().lighting());

        DungeonManager reloaded = DungeonManager.load(manager.save(new CompoundTag(), null), null);
        assertEquals(2, reloaded.byId(7).orElseThrow().lighting());
    }

    @Test
    void theStoredLightingIsReadBackAndClamped() {
        assertEquals(3, DungeonManager.load(rootWith(dungeonTag(1, 3)), null).byId(1).orElseThrow().lighting());
        assertEquals(3, DungeonManager.load(rootWith(dungeonTag(1, 9)), null).byId(1).orElseThrow().lighting());
        assertEquals(0, DungeonManager.load(rootWith(dungeonTag(1, -2)), null).byId(1).orElseThrow().lighting());
    }

    @Test
    void settingTheLightingClampsAndBumpsTheRevisionOnlyOnChange() {
        DungeonManager manager = DungeonManager.load(rootWith(dungeonTag(7, null)), null);
        long revision = manager.revision();

        assertEquals(DungeonManager.Reason.OK, manager.setLighting(7, 9));
        assertEquals(DungeonManager.MAX_LIGHTING, manager.byId(7).orElseThrow().lighting());
        assertEquals(revision + 1, manager.revision());

        assertEquals(DungeonManager.Reason.OK, manager.setLighting(7, DungeonManager.MAX_LIGHTING));
        assertEquals(revision + 1, manager.revision());

        assertEquals(DungeonManager.Reason.OK, manager.setLighting(7, -5));
        assertEquals(0, manager.byId(7).orElseThrow().lighting());
        assertEquals(revision + 2, manager.revision());

        assertEquals(DungeonManager.Reason.NOT_FOUND, manager.setLighting(8, 1));
    }

    private static CompoundTag rootWith(CompoundTag dungeon) {
        CompoundTag root = new CompoundTag();
        root.putInt("formatVersion", 1);
        ListTag list = new ListTag();
        list.add(dungeon);
        root.put("dungeons", list);
        root.putInt("nextId", 8);
        return root;
    }

    private static CompoundTag dungeonTag(int id, Integer lighting) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", "Тест");
        tag.putString("dimension", OVERWORLD.location().toString());
        tag.putLong("core", new BlockPos(50, 64, -60).asLong());
        tag.putLong("createdAt", 1L);
        ListTag chunks = new ListTag();
        chunks.add(new ClaimKey(OVERWORLD, new ChunkPos(3, -4)).save());
        tag.put("chunks", chunks);
        if (lighting != null) {
            tag.putInt("lighting", lighting);
        }
        return tag;
    }
}
