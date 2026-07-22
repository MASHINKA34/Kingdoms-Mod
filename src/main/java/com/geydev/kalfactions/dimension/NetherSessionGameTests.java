package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.dimension.DimensionControlManager.EntryStatus;
import com.geydev.kalfactions.dimension.DimensionControlManager.LandingPos;
import com.geydev.kalfactions.dimension.DimensionControlManager.PortalBounds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetherSessionGameTests {
    private static final DimensionControlManager.LandingAllocator LANDING =
            (occupied, previous, rules) -> Optional.of(new LandingPos(1200, 64, 1200));

    @GameTest(template = "empty")
    public static void safeLandingAcceptsSolidOpenGeometry(GameTestHelper helper) {
        BlockPos feetRelative = new BlockPos(3, 93, 3);
        prepareLanding(helper, feetRelative);

        helper.assertTrue(
                NetherLandingFinder.isSafe(helper.getLevel(), helper.absolutePos(feetRelative)),
                "Solid open landing was rejected"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsafeLandingRejectsFluidAndObstruction(GameTestHelper helper) {
        BlockPos feetRelative = new BlockPos(3, 93, 3);
        prepareLanding(helper, feetRelative);
        helper.setBlock(feetRelative.below(), Blocks.LAVA);
        helper.assertTrue(
                !NetherLandingFinder.isSafe(helper.getLevel(), helper.absolutePos(feetRelative)),
                "Fluid floor was accepted"
        );
        helper.setBlock(feetRelative.below(), Blocks.STONE);
        helper.setBlock(feetRelative.above(2), Blocks.NETHERRACK);
        helper.assertTrue(
                !NetherLandingFinder.isSafe(helper.getLevel(), helper.absolutePos(feetRelative)),
                "Obstructed landing was accepted"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void portalBoundsNormalizeAndOperatorBypassesClosure(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            manager.setNetherPortal(new PortalBounds(10, 90, 8, 4, 60, 2));
            helper.assertTrue(manager.isInsideRegisteredPortal(new BlockPos(4, 60, 2)), "Minimum corner was rejected");
            helper.assertTrue(manager.isInsideRegisteredPortal(new BlockPos(10, 90, 8)), "Maximum corner was rejected");
            helper.assertTrue(!manager.isInsideRegisteredPortal(new BlockPos(11, 70, 5)), "Outside point was accepted");
            manager.setClosed(Level.NETHER, true);
            Instant now = Instant.parse("2026-07-22T15:00:00Z");
            UUID faction = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            helper.assertTrue(
                    manager.authorizeNetherEntry(faction, player, now, true, LANDING).status()
                            == EntryStatus.OPERATOR_BYPASS,
                    "Operator was not allowed through a closed dimension"
            );
            helper.assertTrue(
                    manager.authorizeNetherEntry(faction, player, now, false, LANDING).status()
                            == EntryStatus.SCHEDULE_CLOSED,
                    "Ordinary player bypassed a closed dimension"
            );
        });
    }

    @GameTest(template = "empty")
    public static void returnBindingRejectsFakeExpiredAndReplay(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            Instant start = Instant.parse("2026-07-22T15:00:00Z");
            UUID firstFaction = UUID.randomUUID();
            UUID firstPlayer = UUID.randomUUID();
            var firstSession = manager.authorizeNetherEntry(firstFaction, firstPlayer, start, false, LANDING).session();
            ReturnBinding first = manager.issueReturn(firstSession.sessionId(), firstPlayer, start.plusSeconds(1)).orElseThrow();
            ReturnBinding fake = new ReturnBinding(first.playerId(), first.sessionId(), UUID.randomUUID());
            helper.assertTrue(!manager.isValidReturn(fake, start.plusSeconds(2)), "Fake token was accepted");
            helper.assertTrue(manager.consumeReturn(first, start.plusSeconds(2)), "Valid token was rejected");
            helper.assertTrue(!manager.consumeReturn(first, start.plusSeconds(2)), "Consumed token was replayed");

            UUID secondFaction = UUID.randomUUID();
            UUID secondPlayer = UUID.randomUUID();
            var secondSession = manager.authorizeNetherEntry(secondFaction, secondPlayer, start, false, LANDING).session();
            ReturnBinding expiring = manager.issueReturn(
                    secondSession.sessionId(), secondPlayer, start.plusSeconds(1)
            ).orElseThrow();
            manager.expireSessions(start.plusSeconds(5401), id -> true);
            helper.assertTrue(!manager.isValidReturn(expiring, start.plusSeconds(5402)), "Expired token was accepted");
        });
    }

    @GameTest(template = "empty")
    public static void restartPreservesWallClockSession(GameTestHelper helper) {
        isolated(helper, path -> {
            Instant start = Instant.parse("2026-07-22T15:00:00Z");
            UUID faction = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            DimensionControlManager first = DimensionControlManager.forTesting(path);
            UUID sessionId = first.authorizeNetherEntry(faction, player, start, false, LANDING).session().sessionId();

            DimensionControlManager restarted = DimensionControlManager.forTesting(path);
            helper.assertTrue(
                    restarted.activeSession(faction, start.plusSeconds(600)).orElseThrow().sessionId().equals(sessionId),
                    "Restart lost active session"
            );
            helper.assertTrue(
                    restarted.activeSession(faction, start.plusSeconds(5401)).isEmpty(),
                    "Wall-clock expired session remained active"
            );
            helper.assertTrue(
                    restarted.expireSessions(start.plusSeconds(5401), id -> true).size() == 1,
                    "Expired session was not retired exactly once"
            );
            helper.assertTrue(
                    restarted.expireSessions(start.plusSeconds(5402), id -> true).isEmpty(),
                    "Expired session was retired more than once"
            );
        });
    }

    @GameTest(template = "empty")
    public static void factionJoinsTwoSessionsAndHitsDailyLimit(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            UUID faction = UUID.randomUUID();
            UUID firstPlayer = UUID.randomUUID();
            UUID secondPlayer = UUID.randomUUID();
            Instant firstStart = Instant.parse("2026-07-22T15:00:00Z");
            var first = manager.authorizeNetherEntry(faction, firstPlayer, firstStart, false, LANDING);
            var joined = manager.authorizeNetherEntry(faction, secondPlayer, firstStart.plusSeconds(1), false, LANDING);
            helper.assertTrue(first.status() == EntryStatus.STARTED_SESSION, "First session did not start");
            helper.assertTrue(joined.status() == EntryStatus.JOINED_ACTIVE, "Faction member did not join for free");
            helper.assertTrue(first.session().sessionId().equals(joined.session().sessionId()), "Join created another session");
            manager.expireSessions(firstStart.plusSeconds(5401), id -> true);

            Instant secondStart = Instant.parse("2026-07-22T16:31:00Z");
            helper.assertTrue(
                    manager.authorizeNetherEntry(faction, firstPlayer, secondStart, false, LANDING).status()
                            == EntryStatus.STARTED_SESSION,
                    "Second daily session did not start"
            );
            manager.expireSessions(secondStart.plusSeconds(5401), id -> true);
            helper.assertTrue(
                    manager.authorizeNetherEntry(
                            faction, firstPlayer, Instant.parse("2026-07-22T18:02:00Z"), false, LANDING
                    ).status() == EntryStatus.NO_SESSIONS_LEFT,
                    "Third daily session was accepted"
            );
        });
    }

    private static void prepareLanding(GameTestHelper helper, BlockPos feet) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.setBlock(feet.offset(x, -1, z), Blocks.STONE);
                for (int y = 0; y <= 2; y++) {
                    helper.setBlock(feet.offset(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static void isolated(GameTestHelper helper, Scenario scenario) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("kingdoms-nether-gametest-");
            scenario.run(directory.resolve("state.json"));
            helper.succeed();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            delete(directory);
        }
    }

    private static void delete(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    private interface Scenario {
        void run(Path path);
    }

    private NetherSessionGameTests() {
    }
}
