package com.geydev.kalfactions.sanctuary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

final class SanctuaryManagerTest {
    @Test
    void automaticAreaIsOneContinuousChunkSnappedSquare() {
        BlockPos anchor = new BlockPos(7, 80, -9);
        Set<ClaimKey> claims = SanctuaryManager.calculateAutomaticClaims(Level.OVERWORLD, anchor, 200);

        assertEquals(26 * 26, claims.size());
        for (int chunkX = -13; chunkX <= 12; chunkX++) {
            for (int chunkZ = -14; chunkZ <= 11; chunkZ++) {
                assertTrue(claims.contains(new ClaimKey(Level.OVERWORLD, chunkX, chunkZ)));
            }
        }
        assertFalse(claims.contains(new ClaimKey(Level.OVERWORLD, -14, -14)));
        assertFalse(claims.contains(new ClaimKey(Level.OVERWORLD, 13, 11)));
        assertFalse(claims.contains(new ClaimKey(Level.OVERWORLD, 12, 12)));
    }

    @Test
    void relocatingAnchorLeavesNoAutomaticTail() {
        SanctuaryManager manager = new SanctuaryManager();
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1_024, 64, -768);
        Set<ClaimKey> firstClaims = SanctuaryManager.calculateAutomaticClaims(Level.OVERWORLD, first, 200);
        Set<ClaimKey> secondClaims = SanctuaryManager.calculateAutomaticClaims(Level.OVERWORLD, second, 200);

        manager.replaceAutomaticClaims(Level.OVERWORLD, first, 200, true);
        manager.replaceAutomaticClaims(Level.OVERWORLD, second, 200, true);

        assertEquals(secondClaims, manager.claims());
        assertTrue(firstClaims.stream()
                .filter(key -> !secondClaims.contains(key))
                .noneMatch(manager::isSanctuary));
    }

    @Test
    void explicitRemovalHandlesAutomaticAndManualLayers() {
        SanctuaryManager manager = new SanctuaryManager();
        BlockPos anchor = BlockPos.ZERO;
        manager.replaceAutomaticClaims(Level.OVERWORLD, anchor, 200, true);
        ClaimKey automatic = new ClaimKey(Level.OVERWORLD, 0, 0);
        ClaimKey manual = new ClaimKey(Level.OVERWORLD, 100, 100);

        long before = manager.revision();
        assertTrue(manager.setClaim(automatic, false));
        assertFalse(manager.isSanctuary(automatic));
        assertTrue(manager.revision() > before);

        assertTrue(manager.setClaim(manual, true));
        assertTrue(manager.isSanctuary(manual));
        assertEquals(1, manager.clearManualClaims(Level.OVERWORLD));
        assertFalse(manager.isSanctuary(manual));

        assertTrue(manager.clearAutomaticSpawn());
        assertTrue(manager.claims().isEmpty());
    }

    @Test
    void savedLayersAndAutomaticExclusionsRoundTrip() {
        SanctuaryManager manager = new SanctuaryManager();
        manager.replaceAutomaticClaims(Level.OVERWORLD, BlockPos.ZERO, 200, true);
        ClaimKey excluded = new ClaimKey(Level.OVERWORLD, 0, 0);
        ClaimKey manual = new ClaimKey(Level.OVERWORLD, 40, 40);
        manager.setClaim(excluded, false);
        manager.setClaim(manual, true);

        CompoundTag saved = manager.save(new CompoundTag(), null);
        SanctuaryManager loaded = SanctuaryManager.load(saved, null);

        assertFalse(loaded.isSanctuary(excluded));
        assertTrue(loaded.isSanctuary(manual));
        assertEquals(manager.claims(), loaded.claims());
    }

    @Test
    void legacyScatteredAutomaticDataIsNormalizedFromAnchor() {
        BlockPos anchor = new BlockPos(31, 64, -47);
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("automaticInitialized", true);
        legacy.putLong("automaticAnchor", anchor.asLong());
        ListTag scattered = new ListTag();
        scattered.add(new ClaimKey(Level.OVERWORLD, 900, -900).save());
        scattered.add(new ClaimKey(Level.OVERWORLD, -500, 700).save());
        legacy.put("automaticClaims", scattered);

        SanctuaryManager loaded = SanctuaryManager.load(legacy, null);
        long before = loaded.revision();
        assertTrue(loaded.replaceAutomaticClaims(Level.OVERWORLD, anchor, 200, false));

        assertEquals(
                SanctuaryManager.calculateAutomaticClaims(Level.OVERWORLD, anchor, 200),
                loaded.claims()
        );
        assertNotEquals(before, loaded.revision());
    }
}
