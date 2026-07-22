package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class ResearchManager {
    public static FactionManager.StartResearchResult start(
            FactionManager manager,
            UUID factionId,
            ResearchNode node,
            ServerPlayer player
    ) {
        int crystalCost = ResearchCrystalCosts.forTier(node.tier());
        return manager.startResearch(
                factionId,
                node,
                System.currentTimeMillis(),
                crystalCost,
                new PlayerResearchCrystalPayment(player.getInventory())
        );
    }

    public static int tick(FactionManager manager) {
        return manager.completeFinishedResearch(
                System.currentTimeMillis(),
                ModConfigSpec.INFLUENCE_BASELINE_PER_NODE.getAsLong()
        );
    }

    private ResearchManager() {
    }
}
