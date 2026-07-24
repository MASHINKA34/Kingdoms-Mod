package com.geydev.kalfactions.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientClaimStoreTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.withDefaultNamespace("overworld");

    @AfterEach
    void clearStore() {
        ClientClaimStore.clear();
    }

    @Test
    void blackZoneStartsOutsideSquareRedBoundary() {
        replace(Map.of());

        assertNull(ClientClaimStore.get(Level.OVERWORLD, 499, 0));
        assertEquals(
                ClientClaimStore.BLACK_ZONE_ID,
                ClientClaimStore.get(Level.OVERWORLD, 500, 0).factionId()
        );
        assertNull(ClientClaimStore.get(Level.OVERWORLD, -500, 0));
        assertEquals(
                ClientClaimStore.BLACK_ZONE_ID,
                ClientClaimStore.get(Level.OVERWORLD, -501, 0).factionId()
        );
    }

    @Test
    void explicitTerritoryOverridesProceduralBlackOverlay() {
        long packed = ChunkPos.asLong(500, 0);
        ClientClaimStore.ClaimInfo faction = new ClientClaimStore.ClaimInfo(
                0xCC2200,
                "Legacy",
                UUID.randomUUID(),
                false,
                false,
                false,
                false,
                false
        );
        replace(Map.of(packed, faction));

        assertSame(faction, ClientClaimStore.get(Level.OVERWORLD, 500, 0));
    }

    @Test
    void xaeroRegionDetectionIncludesProceduralZone() {
        replace(Map.of());

        assertFalse(ClientClaimStore.regionHasClaims(Level.OVERWORLD, 0, 0));
        assertTrue(ClientClaimStore.regionHasClaims(Level.OVERWORLD, 15, 0));
        assertTrue(ClientClaimStore.regionHash(Level.OVERWORLD, 15, 0) != 0);
    }

    private static void replace(Map<Long, ClientClaimStore.ClaimInfo> claims) {
        ClientClaimStore.replace(
                OVERWORLD,
                claims,
                new ClientClaimStore.ViewerInfo(new UUID(0L, 0L), 0, 0.0D),
                new ClientClaimStore.ZoneInfo(0, 0, 8_000, true)
        );
    }
}
