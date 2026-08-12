package com.geydev.kalfactions.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.net.FactionPayloads;
import java.util.HashMap;
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

    @Test
    void proceduralZoneGeometryChangesRegionHash() {
        replace(Map.of());
        int original = ClientClaimStore.regionHash(Level.OVERWORLD, 15, 0);

        ClientClaimStore.replace(
                OVERWORLD,
                Map.of(),
                new ClientClaimStore.ViewerInfo(new UUID(0L, 0L), 0, 0.0D),
                new ClientClaimStore.ZoneInfo(16, 0, 7_900, true)
        );

        assertTrue(original != ClientClaimStore.regionHash(Level.OVERWORLD, 15, 0));
    }

    @Test
    void quarryEntryWinsRegardlessOfPayloadOrder() {
        Map<Long, ClientClaimStore.ClaimInfo> claims = new HashMap<>();
        FactionPayloads.ClaimEntry quarry = entry(true, false, "");
        FactionPayloads.ClaimEntry faction = entry(false, false, "Faction");
        FactionPayloads.ClaimEntry sanctuary = entry(false, true, "Spawn");

        ClientFactionPayloadHandler.mergeClaim(claims, quarry);
        ClientFactionPayloadHandler.mergeClaim(claims, sanctuary);
        ClientFactionPayloadHandler.mergeClaim(claims, faction);

        assertTrue(claims.get(ChunkPos.asLong(500, 0)).quarry());
    }

    private static FactionPayloads.ClaimEntry entry(boolean quarry, boolean sanctuary, String name) {
        return new FactionPayloads.ClaimEntry(
                500,
                0,
                0x777777,
                name,
                UUID.randomUUID(),
                false,
                false,
                sanctuary,
                false,
                quarry,
                false
        );
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
