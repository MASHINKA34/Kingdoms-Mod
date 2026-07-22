package com.geydev.kalfactions.integration.xaero.archive;

import com.geydev.kalfactions.faction.FactionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;

public final class XaeroArchiveAccess {
    private static volatile AnchorValidator anchorValidator = (level, position) -> false;

    public static void installAnchorValidator(AnchorValidator validator) {
        anchorValidator = Objects.requireNonNull(validator);
    }

    public static AccessResult authorize(ServerPlayer player, BlockPos anchor, ResourceLocation dimension) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().equals(dimension)) {
            return AccessResult.denied("kingdoms.xaero_archive.error.dimension");
        }
        if (!level.hasChunkAt(anchor) || player.distanceToSqr(anchor.getCenter()) > XaeroArchiveLimits.MAX_ANCHOR_DISTANCE_SQUARED) {
            return AccessResult.denied("kingdoms.xaero_archive.error.distance");
        }
        if (!anchorValidator.isArchiveAnchor(level, anchor)) {
            return AccessResult.denied("kingdoms.xaero_archive.error.anchor");
        }
        FactionManager manager = FactionManager.get(level);
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            return AccessResult.denied("kingdoms.xaero_archive.error.no_faction");
        }
        UUID claimFaction = manager.getFactionAt(level, anchor).map(faction -> faction.id()).orElse(null);
        if (!factionId.equals(claimFaction)) {
            return AccessResult.denied("kingdoms.xaero_archive.error.claim");
        }
        return AccessResult.allowed(factionId);
    }

    @FunctionalInterface
    public interface AnchorValidator {
        boolean isArchiveAnchor(ServerLevel level, BlockPos position);
    }

    public record AccessResult(boolean allowed, UUID factionId, String errorKey) {
        private static AccessResult denied(String errorKey) {
            return new AccessResult(false, null, errorKey);
        }

        private static AccessResult allowed(UUID factionId) {
            return new AccessResult(true, factionId, "");
        }
    }

    private XaeroArchiveAccess() {
    }
}
