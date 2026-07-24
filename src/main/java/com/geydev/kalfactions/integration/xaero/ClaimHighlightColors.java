package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientClaimStore.ClaimInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class ClaimHighlightColors {
    private static final int FILL_ALPHA = 0x50;
    private static final int BORDER_ALPHA = 0xC0;
    private static final int BLACK_FILL_ALPHA = 0xB8;
    private static final int BLACK_BORDER_ALPHA = 0xF0;
    private static final int QUARRY_FILL_ALPHA = 0x78;
    private static final int QUARRY_BORDER_ALPHA = 0xD8;

    static void fill(ResourceKey<Level> dimension, int chunkX, int chunkZ, ClaimInfo self, int[] result) {
        ColorProfile profile = profile(self);
        result[0] = pack(profile.color(), profile.fillAlpha());
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
        if (self.frozen()) {
            int red = pack(0xFF4545, 0xE0);
            result[0] = pack(0xB03030, 0x60);
            result[1] = red;
            result[2] = red;
            result[3] = red;
            result[4] = red;
        }
    }

    private static int side(ResourceKey<Level> dimension, int chunkX, int chunkZ, ClaimInfo self) {
        ColorProfile profile = profile(self);
        ClaimInfo neighbor = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (neighbor != null && neighbor.factionId().equals(self.factionId())) {
            return pack(profile.color(), profile.fillAlpha());
        }
        return pack(profile.color(), profile.borderAlpha());
    }

    static ColorProfile profile(ClaimInfo claim) {
        if (claim.factionId().equals(ClientClaimStore.BLACK_ZONE_ID)) {
            return new ColorProfile(0x080808, BLACK_FILL_ALPHA, BLACK_BORDER_ALPHA);
        }
        if (claim.quarry()) {
            return new ColorProfile(claim.color(), QUARRY_FILL_ALPHA, QUARRY_BORDER_ALPHA);
        }
        return new ColorProfile(claim.color(), FILL_ALPHA, BORDER_ALPHA);
    }

    private static int pack(int rgb, int alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return blue << 24 | green << 16 | red << 8 | alpha;
    }

    record ColorProfile(int color, int fillAlpha, int borderAlpha) {
    }

    private ClaimHighlightColors() {
    }
}
