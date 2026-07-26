package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import java.util.Set;

public final class DrillTerritory {
    public static boolean owns(Faction faction, ClaimKey key) {
        return faction != null && (faction.hasClaim(key) || faction.isOutpostChunk(key));
    }

    public static Set<ClaimKey> of(Faction faction, ClaimKey drillClaim) {
        if (faction == null) {
            return Set.of();
        }
        return faction.outpostAt(drillClaim)
                .<Set<ClaimKey>>map(outpost -> Set.copyOf(outpost.chunks()))
                .orElseGet(faction::claims);
    }

    public static boolean reaches(Faction faction, ClaimKey drillClaim, ClaimKey targetClaim) {
        return of(faction, drillClaim).contains(targetClaim);
    }

    private DrillTerritory() {
    }
}
