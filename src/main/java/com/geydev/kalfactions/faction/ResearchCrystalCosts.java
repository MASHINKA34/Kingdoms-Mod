package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.List;

public final class ResearchCrystalCosts {
    public static final int MAX_SYNCED_TIERS = 6;

    public static int forTier(int tier) {
        return forTier(tier, configured());
    }

    public static int forTier(int tier, List<Integer> costs) {
        if (costs == null || costs.size() < MAX_SYNCED_TIERS) {
            throw new IllegalArgumentException("Six research crystal costs are required");
        }
        return Math.max(0, costs.get(Math.clamp(tier, 1, MAX_SYNCED_TIERS) - 1));
    }

    public static List<Integer> configured() {
        return List.of(
                ModConfigSpec.RESEARCH_CRYSTAL_COST_TIER_1.getAsInt(),
                ModConfigSpec.RESEARCH_CRYSTAL_COST_TIER_2.getAsInt(),
                ModConfigSpec.RESEARCH_CRYSTAL_COST_TIER_3.getAsInt(),
                ModConfigSpec.RESEARCH_CRYSTAL_COST_TIER_4.getAsInt(),
                ModConfigSpec.RESEARCH_CRYSTAL_COST_TIER_5.getAsInt(),
                ModConfigSpec.RESEARCH_CRYSTAL_COST_TIER_6_PLUS.getAsInt()
        );
    }

    private ResearchCrystalCosts() {
    }
}
