package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientClaimStore.ClaimInfo;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

final class KingdomsWorldMapHighlighter extends ChunkHighlighter {
    KingdomsWorldMapHighlighter() {
        super(false);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return ClientClaimStore.regionHash(dimension, regionX, regionZ);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return ClientClaimStore.regionHasClaims(dimension, regionX, regionZ);
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
        ClaimHighlightColors.fill(dimension, chunkX, chunkZ, self, resultStore);
        return resultStore;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        ClaimInfo claim = ClientClaimStore.get(dimension, chunkX, chunkZ);
        if (claim == null || claim.name().isBlank()) {
            return null;
        }
        return KingdomsHighlighter.claimLabel(claim);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(
            List<Component> lines,
            ResourceKey<Level> dimension,
            int blockX,
            int blockZ,
            int height
    ) {
        ClaimInfo claim = ClientClaimStore.get(dimension, blockX >> 4, blockZ >> 4);
        if (claim != null && !claim.name().isBlank()) {
            lines.add(KingdomsHighlighter.claimLabel(claim));
            if (claim.forceLoaded()) {
                lines.add(Component.translatable("kingdoms.xaero.forceload_label"));
            }
            if (claim.frozen()) {
                lines.add(Component.translatable("kingdoms.xaero.frozen_label"));
            }
        }
    }
}
