package com.geydev.kalfactions.outpost.trader;

import java.util.UUID;

public final class TraderAccessPolicy {
    public static boolean canUseWandering(
            UUID memberFaction,
            UUID targetFaction,
            UUID claimFaction,
            UUID entityId,
            UUID eventId,
            long entityExpiresAt,
            TraderWorldData.WanderingEvent event,
            long now
    ) {
        return memberFaction != null
                && memberFaction.equals(targetFaction)
                && memberFaction.equals(claimFaction)
                && event != null
                && event.active()
                && event.factionId().equals(targetFaction)
                && event.entityId().equals(entityId)
                && event.eventId().equals(eventId)
                && entityExpiresAt > now
                && event.expiresAt() > now;
    }

    private TraderAccessPolicy() {
    }
}
