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
import net.minecraft.world.level.Level;
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
        assertEquals(EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                manager.authorizeNetherEntry(faction, second, start.plusSeconds(3), false, LANDING).status());
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
        assertEquals(EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                restarted.authorizeNetherEntry(faction, player, secondStart, false, LANDING).status());
        assertEquals(EntryStatus.STARTED_SESSION,
                restarted.authorizeNetherEntry(faction, player, secondStart, false, true, LANDING).status());
        restarted.expireSessions(secondStart.plusSeconds(5401), id -> true);
        assertEquals(
                EntryStatus.NO_SESSIONS_LEFT,
                restarted.authorizeNetherEntry(
                        faction, player, Instant.parse("2026-07-22T18:02:00Z"), false, LANDING
                ).status()
        );
    }

    @Test
    void lateEntryStartsAndEndsAtMoscowClosing() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T19:50:00Z");

        var result = manager.authorizeNetherEntry(faction, player, start, false, LANDING);

        assertEquals(EntryStatus.STARTED_SESSION, result.status());
        assertEquals(Instant.parse("2026-07-22T20:00:00Z"), result.session().endsAt());
        assertEquals(1, manager.remainingSessions(faction, start));
    }

    @Test
    void confirmedSecondSessionRunsInParallelAndReentryMovesToNewestSession() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID survivor = UUID.randomUUID();
        UUID dead = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");
        var first = manager.authorizeNetherEntry(faction, survivor, start, false, LANDING).session();
        manager.authorizeNetherEntry(faction, dead, start.plusSeconds(1), false, LANDING);
        assertTrue(manager.markDeath(faction, dead, start.plusSeconds(2)));

        assertEquals(EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                manager.authorizeNetherEntry(faction, dead, start.plusSeconds(3), false, LANDING).status());
        assertEquals(1, manager.remainingSessions(faction, start.plusSeconds(3)));
        var second = manager.authorizeNetherEntry(
                faction, dead, start.plusSeconds(4), false, true, LANDING
        ).session();

        assertEquals(2, manager.activeSessions(faction, start.plusSeconds(5)).size());
        assertEquals(first.sessionId(), manager.assignedSession(survivor, start.plusSeconds(5)).orElseThrow().sessionId());
        assertEquals(second.sessionId(), manager.assignedSession(dead, start.plusSeconds(5)).orElseThrow().sessionId());
        assertFalse(first.endsAt().equals(second.endsAt()));
        assertEquals(0, manager.remainingSessions(faction, start.plusSeconds(5)));

        DimensionControlManager restarted = manager();
        assertEquals(2, restarted.activeSessions(faction, start.plusSeconds(5)).size());
        assertEquals(
                first.sessionId(),
                restarted.assignedSession(survivor, start.plusSeconds(5)).orElseThrow().sessionId()
        );
        assertEquals(
                second.sessionId(),
                restarted.assignedSession(dead, start.plusSeconds(5)).orElseThrow().sessionId()
        );

        restarted.leaveNether(survivor);
        var rejoined = restarted.authorizeNetherEntry(faction, survivor, start.plusSeconds(6), false, LANDING);
        assertEquals(EntryStatus.JOINED_ACTIVE, rejoined.status());
        assertEquals(second.sessionId(), rejoined.session().sessionId());
    }

    @Test
    void secondSessionIsNeverConsumedWithoutExplicitConfirmation() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");
        manager.authorizeNetherEntry(faction, player, start, false, LANDING);
        manager.expireSessions(start.plusSeconds(5401), id -> true);

        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals(EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                    manager.authorizeNetherEntry(
                            faction, player, start.plusSeconds(5402 + attempt), false, LANDING
                    ).status());
        }
        assertEquals(1, manager.remainingSessions(faction, start.plusSeconds(5405)));
        assertTrue(manager.activeSessions(faction, start.plusSeconds(5405)).isEmpty());
    }

    @Test
    void differentFactionsReceiveDifferentPersistedLandings() {
        DimensionControlManager manager = manager();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");
        DimensionControlManager.LandingAllocator separated = (occupied, previous, rules) -> Optional.of(
                new LandingPos(1200 + occupied.size() * 1000, 64, 1200)
        );

        var first = manager.authorizeNetherEntry(
                UUID.randomUUID(), UUID.randomUUID(), start, false, separated
        ).session();
        var second = manager.authorizeNetherEntry(
                UUID.randomUUID(), UUID.randomUUID(), start, false, separated
        ).session();

        assertFalse(first.landing().equals(second.landing()));
    }

    @Test
    void disbandEndsSessionAndReturnBindingCannotBeReused() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T15:00:00Z");
        var session = manager.authorizeNetherEntry(faction, player, start, false, LANDING).session();
        BlockPos returnPos = new BlockPos(12, 70, -8);
        ReturnBinding binding = manager.issueReturn(
                session.sessionId(), player, returnPos, start.plusSeconds(1)
        ).orElseThrow();

        assertTrue(manager.isValidReturn(binding, start.plusSeconds(1)));
        assertEquals(returnPos, manager.currentReturn(player, start.plusSeconds(1)).orElseThrow().returnPos());
        assertFalse(manager.isValidReturn(
                new ReturnBinding(player, session.sessionId(), binding.token(), returnPos.above()),
                start.plusSeconds(1)
        ));
        assertTrue(manager.consumeReturn(binding, start.plusSeconds(1)));
        assertFalse(manager.consumeReturn(binding, start.plusSeconds(1)));
        ReturnBinding replacement = manager.issueReturn(session.sessionId(), player, start.plusSeconds(1)).orElseThrow();
        assertFalse(replacement.token().equals(binding.token()));
        assertEquals(1, manager.expireSessions(start.plusSeconds(2), id -> false).size());
        assertFalse(manager.isValidReturn(replacement, start.plusSeconds(3)));
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
    void netherWipeIsOnlyScheduledExplicitly() {
        DimensionControlManager manager = manager();

        assertFalse(manager.isWipePending(Level.NETHER));
        assertFalse(manager.setWipePending(Level.NETHER, true));
        assertTrue(manager.requestNetherWipeFromDimensionKey());
        assertTrue(manager.isWipePending(Level.NETHER));
        assertTrue(manager.cancelNetherWipeFromDimensionKey());
        assertFalse(manager.isWipePending(Level.NETHER));
    }

    @Test
    void pendingNetherWipePersistsAndCompletesOnlyOnceAtSafeStartup() {
        DimensionControlManager manager = manager();
        assertTrue(manager.requestNetherWipeFromDimensionKey());

        DimensionControlManager restarted = manager();
        assertTrue(restarted.isWipePending(Level.NETHER));
        assertTrue(restarted.completePendingWipe(Level.NETHER, 12345L));
        assertFalse(restarted.isWipePending(Level.NETHER));
        assertEquals(1L, restarted.wipeGeneration(Level.NETHER));
        assertFalse(restarted.completePendingWipe(Level.NETHER, 12345L));

        DimensionControlManager afterCompletion = manager();
        assertFalse(afterCompletion.isWipePending(Level.NETHER));
        assertEquals(1L, afterCompletion.wipeGeneration(Level.NETHER));
    }

    @Test
    void wipeTargetMustStayInsideWorldRoot() {
        Path worldRoot = temporary.resolve("world");

        assertTrue(DimensionControlManager.isSafeWipeFolder(worldRoot, worldRoot.resolve("DIM-1")));
        assertFalse(DimensionControlManager.isSafeWipeFolder(worldRoot, worldRoot));
        assertFalse(DimensionControlManager.isSafeWipeFolder(worldRoot, worldRoot.resolve("..").resolve("other-world")));
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

    @Test
    void netherOpenNotificationFiresOnceAtMoscowOpeningMinute() {
        DimensionControlManager manager = manager();

        assertFalse(manager.claimNetherOpenNotification(Instant.parse("2026-07-22T14:59:59Z")));
        assertTrue(manager.claimNetherOpenNotification(Instant.parse("2026-07-22T15:00:00Z")));
        assertFalse(manager.claimNetherOpenNotification(Instant.parse("2026-07-22T15:00:30Z")));
        assertFalse(manager.claimNetherOpenNotification(Instant.parse("2026-07-22T16:00:00Z")));
        assertTrue(manager.claimNetherOpenNotification(Instant.parse("2026-07-23T15:00:00Z")));
    }

    @Test
    void closedNetherDoesNotAnnounceScheduledOpening() {
        DimensionControlManager manager = manager();
        manager.setClosed(Level.NETHER, true);

        assertFalse(manager.claimNetherOpenNotification(Instant.parse("2026-07-22T15:00:00Z")));
    }

    private DimensionControlManager manager() {
        return DimensionControlManager.forTesting(temporary.resolve("dimension-control.json"));
    }
}
