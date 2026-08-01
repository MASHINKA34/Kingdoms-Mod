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
import java.util.concurrent.atomic.AtomicInteger;
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
    void operatorGetsPersonalSessionAndReturnOutsideSchedule() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID operator = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-22T10:00:00Z");
        manager.setClosed(Level.NETHER, true);
        int factionPassesBefore = manager.remainingSessions(faction, start);

        var preview = manager.previewNetherEntry(faction, operator, start, true, false);
        assertEquals(EntryStatus.OPERATOR_BYPASS, preview.status());
        assertTrue(manager.activeSessions(start).isEmpty());

        var result = manager.authorizeNetherEntry(faction, operator, start, true, LANDING);
        assertEquals(EntryStatus.OPERATOR_BYPASS, result.status());
        assertEquals(0, result.session().ordinal());
        assertEquals(start.plus(manager.rules().sessionDuration()), result.session().endsAt());
        assertEquals(factionPassesBefore, manager.remainingSessions(faction, start));
        assertTrue(manager.activeSessions(faction, start).isEmpty());
        assertTrue(manager.issueReturn(
                result.session().sessionId(), operator, new BlockPos(20, 70, 20), start.plusSeconds(1)
        ).isPresent());

        DimensionControlManager restarted = manager();
        assertEquals(
                result.session().sessionId(),
                restarted.assignedSession(operator, start.plusSeconds(2)).orElseThrow().sessionId()
        );
    }

    @Test
    void playerWithoutFactionIsDeniedBeforeAnyScheduleOrSessionMutation() {
        DimensionControlManager manager = manager();
        UUID player = UUID.randomUUID();
        Instant open = Instant.parse("2026-07-22T15:00:00Z");

        assertEquals(
                EntryStatus.FACTION_REQUIRED,
                manager.previewNetherEntry(null, player, open, false, false).status()
        );
        assertEquals(
                EntryStatus.FACTION_REQUIRED,
                manager.authorizeNetherEntry(null, player, open, false, LANDING).status()
        );
        assertTrue(manager.activeSessions(open).isEmpty());
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
    void sameDaySecondSessionReusesLandingAndNextDayAllocatesANewOne() {
        DimensionControlManager manager = manager();
        UUID faction = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant firstDay = Instant.parse("2026-07-22T15:00:00Z");
        LandingPos firstLanding = new LandingPos(1200, 64, 1200);
        LandingPos nextDayLanding = new LandingPos(-2400, 72, 1800);
        AtomicInteger allocations = new AtomicInteger();
        DimensionControlManager.LandingAllocator allocator = (occupied, previous, rules) -> Optional.of(
                allocations.getAndIncrement() == 0 ? firstLanding : nextDayLanding
        );

        var first = manager.authorizeNetherEntry(faction, player, firstDay, false, allocator).session();
        assertTrue(manager.markDeath(faction, player, firstDay.plusSeconds(1)));
        var second = manager.authorizeNetherEntry(
                faction, player, firstDay.plusSeconds(2), false, true, allocator
        ).session();

        assertEquals(firstLanding, first.landing());
        assertEquals(firstLanding, second.landing());
        assertEquals(1, allocations.get());

        Instant nextDay = Instant.parse("2026-07-23T15:00:00Z");
        var third = manager.authorizeNetherEntry(faction, player, nextDay, false, allocator).session();
        assertEquals(nextDayLanding, third.landing());
        assertEquals(2, allocations.get());
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
    void scheduledOpeningClearsOnlyClosureMadeBeforeDailyOpening() {
        DimensionControlManager manager = manager();
        manager.setClosed(Level.NETHER, true);

        assertFalse(manager.applyScheduledNetherOpening(Instant.parse("2026-07-22T14:59:59Z")));
        assertTrue(manager.applyScheduledNetherOpening(Instant.parse("2026-07-22T15:00:00Z")));
        assertTrue(manager.isNetherOpenForPlayers(Instant.parse("2026-07-22T15:00:01Z")));

        manager.setClosed(Level.NETHER, true);
        assertFalse(manager.applyScheduledNetherOpening(Instant.parse("2026-07-22T16:00:00Z")));
        assertTrue(manager.isClosed(Level.NETHER));
        assertTrue(manager.applyScheduledNetherOpening(Instant.parse("2026-07-23T15:00:00Z")));
        assertFalse(manager.isClosed(Level.NETHER));
    }

    private DimensionControlManager manager() {
        return DimensionControlManager.forTesting(temporary.resolve("dimension-control.json"));
    }
}
