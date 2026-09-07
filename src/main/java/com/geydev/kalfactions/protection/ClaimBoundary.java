package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ClaimBoundary {
    public static UUID ownerAt(ServerLevel level, BlockPos pos) {
        return FactionManager.get(level).getFactionIdAt(ClaimKey.of(level, pos)).orElse(null);
    }

    public static boolean sameChunk(BlockPos first, BlockPos second) {
        return (first.getX() >> 4) == (second.getX() >> 4) && (first.getZ() >> 4) == (second.getZ() >> 4);
    }

    public static boolean crossesClaimBoundary(ServerLevel level, BlockPos from, BlockPos to) {
        if (sameChunk(from, to)) {
            return false;
        }
        FactionManager manager = FactionManager.get(level);
        if (!manager.hasClaims()) {
            return false;
        }
        UUID source = manager.getFactionIdAt(ClaimKey.of(level, from)).orElse(null);
        UUID target = manager.getFactionIdAt(ClaimKey.of(level, to)).orElse(null);
        return !sharesOwner(manager, source, target);
    }

    public static boolean sharesOwner(FactionManager manager, UUID first, UUID second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        return first.equals(second) || manager.areAllied(first, second);
    }

    private ClaimBoundary() {
    }
}
