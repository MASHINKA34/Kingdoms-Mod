package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NetherHudServiceTest {
    @TempDir
    Path temporary;

    @Test
    void realHudAppearsAtSeventeenFiftyFiveAndStaysThroughOpenWindow() {
        DimensionControlManager manager = manager();
        UUID player = UUID.randomUUID();

        var before = NetherHudService.realPayload(
                manager, null, player, Instant.parse("2026-07-22T14:54:59Z")
        );
        var opening = NetherHudService.realPayload(
                manager, null, player, Instant.parse("2026-07-22T14:55:00Z")
        );
        var open = NetherHudService.realPayload(
                manager, null, player, Instant.parse("2026-07-22T19:59:59Z")
        );
        var closed = NetherHudService.realPayload(
                manager, null, player, Instant.parse("2026-07-22T20:00:00Z")
        );

        assertFalse(before.visible());
        assertTrue(opening.visible());
        assertTrue(opening.opening());
        assertEquals(Instant.parse("2026-07-22T15:00:00Z").toEpochMilli(), opening.phaseEndsAtEpochMillis());
        assertTrue(open.visible());
        assertFalse(open.opening());
        assertEquals(-1, open.remainingSessions());
        assertFalse(closed.visible());
    }

    @Test
    void openHudCarriesServerSessionDeadline() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");
        var session = manager.authorizeNetherEntry(
                faction,
                player,
                start,
                false,
                (occupied, previous, rules) -> Optional.of(new DimensionControlManager.LandingPos(1200, 64, 1200))
        ).session();

        var payload = NetherHudService.realPayload(manager, faction, player, start.plusSeconds(1));

        assertEquals(1, payload.remainingSessions());
        assertEquals(session.endsAt().toEpochMilli(), payload.sessionEndsAtEpochMillis());
    }

    @Test
    void previewDoesNotChangeRealSessionsOrSchedule() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant closedTime = Instant.parse("2026-07-22T12:00:00Z");
        int remainingBefore = manager.remainingSessions(faction, closedTime);

        var preview = NetherHudService.previewPayload(
                manager, faction, player, closedTime, false, 300
        );

        assertTrue(preview.visible());
        assertTrue(preview.preview());
        assertFalse(NetherSchedulePolicy.isOpen(closedTime));
        assertEquals(remainingBefore, manager.remainingSessions(faction, closedTime));
        assertTrue(manager.activeSessions(closedTime).isEmpty());
    }

    private DimensionControlManager manager() {
        return DimensionControlManager.forTesting(temporary.resolve("dimension-control.json"));
    }
}
