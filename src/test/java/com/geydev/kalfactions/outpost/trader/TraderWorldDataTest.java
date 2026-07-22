package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TraderWorldDataTest {
    @Test
    void pointAddNearestRemoveIsBounded() {
        TraderWorldData data = new TraderWorldData();
        TraderWorldData.AddPointResult added = data.addPoint(Level.OVERWORLD, new BlockPos(10, 64, 10), 45.0F);

        assertTrue(added.added());
        assertEquals(added.point(), data.nearestPoint(Level.OVERWORLD, new BlockPos(11, 64, 10), 2.0D).orElseThrow());
        assertTrue(data.removePoint(added.point().id()));
        assertTrue(data.points().isEmpty());
    }

    @Test
    void onlyOneContrabandEventCanBeActive() {
        TraderWorldData data = new TraderWorldData();
        TraderWorldData.SpawnPoint point = point();
        TraderWorldData.ActiveContraband first = active(point, 10_000L);
        TraderWorldData.ActiveContraband second = active(point, 20_000L);

        assertTrue(data.beginContraband(first));
        assertFalse(data.beginContraband(second));
        assertEquals(first, data.contraband().orElseThrow());
    }

    @Test
    void roundTripPreservesLifecycleAndRolledPrices() {
        TraderWorldData data = new TraderWorldData();
        TraderWorldData.SpawnPoint point = point();
        assertTrue(data.addPoint(point));
        TraderWorldData.ActiveContraband active = active(point, 90_000L);
        assertTrue(data.beginContraband(active));
        data.setWanderingNextRollAt(80_000L);
        data.setWanderingRollCursor(513);
        UUID factionId = UUID.randomUUID();
        TraderWorldData.WanderingEvent wandering = new TraderWorldData.WanderingEvent(
                factionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ClaimKey(Level.OVERWORLD, 2, 3),
                new BlockPos(36, 70, 52),
                List.of(new TraderWorldData.RolledOffer("raw_iron", 9L)),
                70_000L,
                0L
        );
        assertTrue(data.putWandering(wandering));

        CompoundTag saved = data.save(new CompoundTag(), null);
        TraderWorldData loaded = TraderWorldData.load(saved, null);

        assertEquals(TraderWorldData.FORMAT_VERSION, saved.getInt("formatVersion"));
        assertEquals(List.of(point), loaded.points());
        assertEquals(active, loaded.contraband().orElseThrow());
        assertEquals(wandering, loaded.wandering(factionId).orElseThrow());
        assertEquals(80_000L, loaded.wanderingNextRollAt());
        assertEquals(513, loaded.wanderingRollCursor());
    }

    @Test
    void finishingWanderingKeepsCooldownWithoutActiveEntity() {
        TraderWorldData data = new TraderWorldData();
        UUID factionId = UUID.randomUUID();
        data.putWandering(new TraderWorldData.WanderingEvent(
                factionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ClaimKey(Level.OVERWORLD, 0, 0),
                BlockPos.ZERO,
                List.of(new TraderWorldData.RolledOffer("coal", 5L)),
                1_000L,
                0L
        ));

        data.finishWandering(factionId, 50_000L);

        TraderWorldData.WanderingEvent finished = data.wandering(factionId).orElseThrow();
        assertFalse(finished.active());
        assertEquals(50_000L, finished.cooldownUntil());
        assertTrue(finished.offers().isEmpty());
    }

    private static TraderWorldData.SpawnPoint point() {
        return new TraderWorldData.SpawnPoint(UUID.randomUUID(), Level.OVERWORLD, new BlockPos(10, 64, 10), 45.0F);
    }

    private static TraderWorldData.ActiveContraband active(TraderWorldData.SpawnPoint point, long expiresAt) {
        return new TraderWorldData.ActiveContraband(
                UUID.randomUUID(), UUID.randomUUID(), point.id(), point.dimension(), point.pos(), expiresAt
        );
    }
}
