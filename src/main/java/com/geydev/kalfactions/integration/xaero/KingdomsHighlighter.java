package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientClaimStore.ClaimInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.common.minimap.highlight.ChunkHighlighter;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;

final class KingdomsHighlighter extends ChunkHighlighter {
    private static final int FILL_ALPHA = 0x90 << 24;

    KingdomsHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return ClientClaimStore.hasClaims(dimension);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return ClientClaimStore.get(dimension, chunkX, chunkZ) != null;
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClaimInfo self = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (self == null) {
            return null;
        }
        return new int[] {
            fill(self),
            side(dimension, chunkX, chunkZ - 1, self),
            side(dimension, chunkX + 1, chunkZ, self),
            side(dimension, chunkX, chunkZ + 1, self),
            side(dimension, chunkX - 1, chunkZ, self)
        };
    }

    @Override
    public void addChunkHighlightTooltips(
            InfoDisplayCompiler compiler,
            ResourceKey<Level> dimension,
            int chunkX,
            int chunkZ,
            int height
    ) {
        ClaimInfo claim = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (claim != null && !claim.name().isBlank()) {
            compiler.addLine(Component.literal(claim.name()));
        }
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
}
