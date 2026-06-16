package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.UUID;

public final class ResearchManager {
    public static FactionManager.StartResearchResult start(
            FactionManager manager,
            UUID factionId,
            ResearchNode node
    ) {
        return manager.startResearch(factionId, node, System.currentTimeMillis());
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
