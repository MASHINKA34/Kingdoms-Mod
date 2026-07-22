package com.geydev.kalfactions.outpost.trader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class TraderAccessPolicyTest {
    @Test
    void onlyTargetFactionInsideCurrentClaimCanTrade() {
        Fixture fixture = fixture();

        assertTrue(fixture.allowed(fixture.factionId(), fixture.factionId(), 2_000L));
        assertFalse(fixture.allowed(UUID.randomUUID(), fixture.factionId(), 2_000L));
        assertFalse(fixture.allowed(fixture.factionId(), UUID.randomUUID(), 2_000L));
    }

    @Test
    void expiredOrMismatchedEventIsRejected() {
        Fixture fixture = fixture();

        assertFalse(fixture.allowed(fixture.factionId(), fixture.factionId(), 20_000L));
        assertFalse(TraderAccessPolicy.canUseWandering(
                fixture.factionId(),
                fixture.factionId(),
                fixture.factionId(),
                fixture.entityId(),
                UUID.randomUUID(),
                10_000L,
                fixture.event(),
                2_000L
        ));
    }

    private static Fixture fixture() {
        UUID factionId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TraderWorldData.WanderingEvent event = new TraderWorldData.WanderingEvent(
                factionId,
                eventId,
                entityId,
                new ClaimKey(Level.OVERWORLD, 0, 0),
                BlockPos.ZERO,
                List.of(new TraderWorldData.RolledOffer("coal", 5L)),
                10_000L,
                0L
        );
        return new Fixture(factionId, entityId, eventId, event);
    }

    private record Fixture(
            UUID factionId,
            UUID entityId,
            UUID eventId,
            TraderWorldData.WanderingEvent event
    ) {
        private boolean allowed(UUID memberFaction, UUID claimFaction, long now) {
            return TraderAccessPolicy.canUseWandering(
                    memberFaction,
                    factionId,
                    claimFaction,
                    entityId,
                    eventId,
                    10_000L,
                    event,
                    now
            );
        }
    }
}
