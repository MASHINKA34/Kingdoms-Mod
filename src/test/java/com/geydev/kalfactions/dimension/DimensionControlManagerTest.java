package com.geydev.kalfactions.dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.dimension.DimensionControlManager.EntryStatus;
import com.geydev.kalfactions.dimension.DimensionControlManager.LandingPos;
import com.geydev.kalfactions.dimension.DimensionControlManager.PortalBounds;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DimensionControlManagerTest {
    private static final DimensionControlManager.LandingAllocator LANDING =
            (occupied, previous, rules) -> Optional.of(new LandingPos(1200, 64, 1200));

    @TempDir
    Path temporary;

    @Test
    void factionSharesSessionAndDeathLocksOnlyTheDeadPlayer() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");

        var started = manager.authorizeNetherEntry(faction, first, start, false, LANDING);
        var joined = manager.authorizeNetherEntry(faction, second, start.plusSeconds(1), false, LANDING);

        assertEquals(EntryStatus.STARTED_SESSION, started.status());
        assertEquals(EntryStatus.JOINED_ACTIVE, joined.status());
        assertEquals(started.session().sessionId(), joined.session().sessionId());
        assertTrue(manager.markDeath(faction, second, start.plusSeconds(2)));
        assertEquals(
                EntryStatus.DEATH_LOCKED,
                manager.authorizeNetherEntry(faction, second, start.plusSeconds(3), false, LANDING).status()
        );
        assertEquals(
                EntryStatus.JOINED_ACTIVE,
                manager.authorizeNetherEntry(faction, first, start.plusSeconds(3), false, LANDING).status()
        );
    }

    @Test
    void sessionsPersistAcrossRestartAndCannotBeReplayedBeyondDailyLimit() {
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant firstStart = Instant.parse("2026-07-22T15:00:00Z");
        DimensionControlManager firstManager = manager();
        UUID session = firstManager.authorizeNetherEntry(faction, player, firstStart, false, LANDING)
                .session().sessionId();

        DimensionControlManager restarted = manager();
        assertEquals(session, restarted.activeSession(faction, firstStart.plusSeconds(600)).orElseThrow().sessionId());
        assertEquals(1, restarted.expireSessions(firstStart.plusSeconds(5401), id -> true).size());
        assertTrue(restarted.expireSessions(firstStart.plusSeconds(5402), id -> true).isEmpty());

        Instant secondStart = Instant.parse("2026-07-22T16:31:00Z");
        assertEquals(
                EntryStatus.STARTED_SESSION,
                restarted.authorizeNetherEntry(faction, player, secondStart, false, LANDING).status()
        );
        restarted.expireSessions(secondStart.plusSeconds(5401), id -> true);
        assertEquals(
                EntryStatus.NO_SESSIONS_LEFT,
                restarted.authorizeNetherEntry(
                        faction, player, Instant.parse("2026-07-22T18:02:00Z"), false, LANDING
                ).status()
        );
    }

    @Test
    void disbandEndsSessionAndReturnBindingCannotBeReused() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");
        var session = manager.authorizeNetherEntry(faction, player, start, false, LANDING).session();
        ReturnBinding binding = manager.issueReturn(session.sessionId(), player, start.plusSeconds(1)).orElseThrow();

        assertTrue(manager.isValidReturn(binding, start.plusSeconds(1)));
        assertTrue(manager.consumeReturn(binding, start.plusSeconds(1)));
        assertFalse(manager.consumeReturn(binding, start.plusSeconds(1)));
        assertTrue(manager.issueReturn(session.sessionId(), player, start.plusSeconds(1)).isEmpty());
        assertEquals(1, manager.expireSessions(start.plusSeconds(2), id -> false).size());
        assertFalse(manager.isValidReturn(binding, start.plusSeconds(3)));
    }

    @Test
    void portalBoundsAreNormalizedAndPersisted() {
        DimensionControlManager manager = manager();
        manager.setNetherPortal(new PortalBounds(10, 90, 8, 4, 60, 2));

        DimensionControlManager restarted = manager();
        assertTrue(restarted.isInsideRegisteredPortal(new BlockPos(4, 60, 2)));
        assertTrue(restarted.isInsideRegisteredPortal(new BlockPos(10, 90, 8)));
        assertFalse(restarted.isInsideRegisteredPortal(new BlockPos(11, 70, 5)));
    }

    @Test
    void wipeBecomesPendingAtTwentyThreeAfterSevenCalendarDays() {
        DimensionControlManager manager = manager();

        assertFalse(manager.updateWipeSchedule(Instant.parse("2026-07-01T20:00:00Z")));
        assertFalse(manager.updateWipeSchedule(Instant.parse("2026-07-08T19:59:59Z")));
        assertTrue(manager.updateWipeSchedule(Instant.parse("2026-07-08T20:00:00Z")));
        assertFalse(manager.updateWipeSchedule(Instant.parse("2026-07-08T20:00:01Z")));
    }

    @Test
    void dailyResetNotificationCanOnlyBeClaimedOncePerMoscowDate() {
        DimensionControlManager manager = manager();

        assertTrue(manager.claimDailyResetNotification(Instant.parse("2026-07-21T21:00:00Z")));
        assertFalse(manager.claimDailyResetNotification(Instant.parse("2026-07-22T15:00:00Z")));
        assertFalse(manager.claimDailyResetNotification(Instant.parse("2026-07-22T19:00:00Z")));
        assertTrue(manager.claimDailyResetNotification(Instant.parse("2026-07-22T21:00:00Z")));
    }

    @Test
    void firstDailyNotificationDoesNotFireAtArbitraryStartupTime() {
        DimensionControlManager manager = manager();

        assertFalse(manager.claimDailyResetNotification(Instant.parse("2026-07-22T10:00:00Z")));
        assertFalse(manager.claimDailyResetNotification(Instant.parse("2026-07-22T20:59:59Z")));
        assertTrue(manager.claimDailyResetNotification(Instant.parse("2026-07-22T21:00:00Z")));
    }

    private DimensionControlManager manager() {
        return DimensionControlManager.forTesting(temporary.resolve("dimension-control.json"));
    }
}
