package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientClaimStore.ClaimInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class ClaimHighlightColors {
    private static final int FILL_ALPHA = 0x50;
    private static final int BORDER_ALPHA = 0xC0;

    static void fill(ResourceKey<Level> dimension, int chunkX, int chunkZ, ClaimInfo self, int[] result) {
        result[0] = pack(self.color(), FILL_ALPHA);
        result[1] = side(dimension, chunkX, chunkZ - 1, self);
        result[2] = side(dimension, chunkX + 1, chunkZ, self);
        result[3] = side(dimension, chunkX, chunkZ + 1, self);
        result[4] = side(dimension, chunkX - 1, chunkZ, self);
        if (self.forceLoaded()) {
            int green = pack(0x5AFF8A, 0xE0);
            result[1] = green;
            result[2] = green;
            result[3] = green;
            result[4] = green;
        }
    }

    private static int side(ResourceKey<Level> dimension, int chunkX, int chunkZ, ClaimInfo self) {
        ClaimInfo neighbor = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (neighbor != null && neighbor.factionId().equals(self.factionId())) {
            return pack(self.color(), FILL_ALPHA);
        }
        return pack(self.color(), BORDER_ALPHA);
    }

    private static int pack(int rgb, int alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return blue << 24 | green << 16 | red << 8 | alpha;
    }

    private ClaimHighlightColors() {
    }
}
