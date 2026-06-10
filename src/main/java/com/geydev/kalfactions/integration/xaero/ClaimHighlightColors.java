package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientClaimStore.ClaimInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class ClaimHighlightColors {
    private static final int FILL_ALPHA = 0x90 << 24;

    static void fill(ResourceKey<Level> dimension, int chunkX, int chunkZ, ClaimInfo self, int[] result) {
        result[0] = fill(self);
        result[1] = side(dimension, chunkX, chunkZ - 1, self);
        result[2] = side(dimension, chunkX + 1, chunkZ, self);
        result[3] = side(dimension, chunkX, chunkZ + 1, self);
        result[4] = side(dimension, chunkX - 1, chunkZ, self);
    }

    private static int fill(ClaimInfo claim) {
        return FILL_ALPHA | (claim.color() & 0xFFFFFF);
    }

    private static int side(ResourceKey<Level> dimension, int chunkX, int chunkZ, ClaimInfo self) {
        ClaimInfo neighbor = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (neighbor == null) {
            return 0;
        }
        return neighbor.factionId().equals(self.factionId()) ? fill(self) : fill(neighbor);
    }

    private ClaimHighlightColors() {
    }
}
