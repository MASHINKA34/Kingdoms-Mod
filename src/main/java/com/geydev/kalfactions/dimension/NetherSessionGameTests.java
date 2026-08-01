package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.dimension.DimensionControlManager.EntryStatus;
import com.geydev.kalfactions.dimension.DimensionControlManager.LandingPos;
import com.geydev.kalfactions.dimension.DimensionControlManager.PortalBounds;
import com.geydev.kalfactions.registry.ModCreativeTabs;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public static void sameSessionMemberFindsSafeNearbyLandingWithoutPortal(GameTestHelper helper) {
        BlockPos memberFeet = new BlockPos(3, 93, 3);
        prepareLanding(helper, memberFeet);
        BlockPos absolute = helper.absolutePos(memberFeet);
        LandingPos nearby = NetherLandingFinder.findNear(helper.getLevel(), absolute).orElseThrow();
        helper.assertTrue(
                nearby.blockPos().closerThan(absolute, 4.0D),
                "Member of the same session was not placed nearby"
        );
        helper.assertTrue(
                !helper.getLevel().getBlockState(nearby.blockPos()).is(Blocks.NETHER_PORTAL),
                "A destination portal was created at the faction landing"
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
    public static void ordinaryPlayerPortalCreationIsRejected(GameTestHelper helper) {
        helper.assertTrue(
                !NetherPortalRegistration.mayCreatePortal(false, true, true),
                "Ordinary player was allowed to create a Nether portal"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void operatorPortalIsDetectedAndRegistered(GameTestHelper helper) {
        BlockPos bottom = new BlockPos(3, 93, 3);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                helper.setBlock(bottom.offset(x, y, 0), Blocks.NETHER_PORTAL);
            }
        }
        var bounds = NetherPortalRegistration.findConnectedPortalBlocks(
                helper.getLevel(), helper.absolutePos(bottom)
        ).orElseThrow();
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            manager.setNetherPortal(bounds);
            DimensionControlManager restarted = DimensionControlManager.forTesting(path);
            helper.assertTrue(
                    restarted.isInsideRegisteredPortal(helper.absolutePos(bottom.offset(1, 2, 0))),
                    "Detected operator portal was not registered persistently"
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
            BlockPos returnPos = new BlockPos(20, 72, -15);
            ReturnBinding first = manager.issueReturn(
                    firstSession.sessionId(), firstPlayer, returnPos, start.plusSeconds(1)
            ).orElseThrow();
            ReturnBinding fake = new ReturnBinding(first.playerId(), first.sessionId(), UUID.randomUUID());
            helper.assertTrue(!manager.isValidReturn(fake, start.plusSeconds(2)), "Fake token was accepted");
            helper.assertTrue(
                    manager.currentReturn(firstPlayer, start.plusSeconds(2)).orElseThrow().returnPos().equals(returnPos),
                    "Return position was not persisted"
            );
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
                            == EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                    "Second daily session did not require confirmation"
            );
            helper.assertTrue(
                    manager.authorizeNetherEntry(faction, firstPlayer, secondStart, false, true, LANDING).status()
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

    @GameTest(template = "empty")
    public static void deathRequiresConfirmationAndStartsParallelSecondSession(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            UUID faction = UUID.randomUUID();
            UUID survivor = UUID.randomUUID();
            UUID dead = UUID.randomUUID();
            Instant start = Instant.parse("2026-07-22T15:00:00Z");
            var first = manager.authorizeNetherEntry(faction, survivor, start, false, LANDING).session();
            manager.authorizeNetherEntry(faction, dead, start.plusSeconds(1), false, LANDING);
            helper.assertTrue(manager.markDeath(faction, dead, start.plusSeconds(2)), "Death was not recorded");
            helper.assertTrue(
                    manager.authorizeNetherEntry(faction, dead, start.plusSeconds(3), false, LANDING).status()
                            == EntryStatus.SECOND_SESSION_CONFIRMATION_REQUIRED,
                    "Portal touch consumed the second session without confirmation"
            );
            helper.assertTrue(manager.remainingSessions(faction, start.plusSeconds(3)) == 1,
                    "Unconfirmed second session was consumed");
            var second = manager.authorizeNetherEntry(
                    faction, dead, start.plusSeconds(60), false, true, LANDING
            ).session();
            helper.assertTrue(
                    manager.activeSessions(faction, start.plusSeconds(61)).size() == 2,
                    "First and second sessions did not run in parallel"
            );
            helper.assertTrue(
                    manager.assignedSession(survivor, start.plusSeconds(61)).orElseThrow().sessionId()
                            .equals(first.sessionId()),
                    "First-session survivor was moved into the second session"
            );
            helper.assertTrue(
                    manager.assignedSession(dead, start.plusSeconds(61)).orElseThrow().sessionId()
                            .equals(second.sessionId()),
                    "Dead player was not assigned to the confirmed second session"
            );
            var ended = manager.expireSessions(start.plusSeconds(5401), id -> true);
            helper.assertTrue(
                    ended.size() == 1 && ended.getFirst().sessionId().equals(first.sessionId()),
                    "Ending the first timer also ended the parallel second session"
            );
            helper.assertTrue(
                    manager.activeSessionById(second.sessionId(), start.plusSeconds(5401)).isPresent(),
                    "Second session did not survive the first timer"
            );
        });
    }

    @GameTest(template = "empty")
    public static void hudWindowAndPreviewDoNotMutateSessions(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            UUID faction = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            Instant openingTime = Instant.parse("2026-07-22T14:55:00Z");
            var opening = NetherHudService.realPayload(manager, faction, player, openingTime);
            helper.assertTrue(opening.visible() && opening.opening(), "HUD did not appear at 17:55 Moscow");
            Instant beforeOpening = Instant.parse("2026-07-22T12:00:00Z");
            int remaining = manager.remainingSessions(faction, beforeOpening);
            var preview = NetherHudService.previewPayload(
                    manager, faction, player, beforeOpening, false, 300
            );
            helper.assertTrue(preview.visible() && preview.preview(), "HUD preview was not visible");
            helper.assertTrue(
                    manager.remainingSessions(faction, beforeOpening) == remaining
                            && manager.activeSessions(beforeOpening).isEmpty(),
                    "HUD preview changed real session data"
            );
        });
    }

    @GameTest(template = "empty")
    public static void returnStoneReservesAndRepairsCentralHotbarSlot(GameTestHelper helper) {
        ServerPlayer player = createPlayer(helper, "netherstone");
        for (int slot = 9; slot <= 35; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE));
        }
        ItemStack protectedItem = new ItemStack(Items.DIAMOND);
        player.getInventory().setItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT, protectedItem);
        helper.assertTrue(
                !NetherReturnIntegration.canPrepareCentralSlot(player),
                "Full main inventory allowed an occupied central slot"
        );
        helper.assertTrue(
                player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT).is(Items.DIAMOND),
                "Failed reservation overwrote the central item"
        );
        player.getInventory().setItem(9, ItemStack.EMPTY);
        helper.assertTrue(NetherReturnIntegration.prepareCentralSlot(player), "Central slot could not be reserved");
        helper.assertTrue(player.getInventory().getItem(9).is(Items.DIAMOND), "Central item was not moved safely");

        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            UUID faction = UUID.randomUUID();
            Instant start = Instant.parse("2026-07-22T15:00:00Z");
            var session = manager.authorizeNetherEntry(faction, player.getUUID(), start, false, LANDING).session();
            ReturnBinding binding = manager.issueReturn(
                    session.sessionId(), player.getUUID(), new BlockPos(2, 70, 2), start.plusSeconds(1)
            ).orElseThrow();
            helper.assertTrue(NetherReturnIntegration.give(player, binding), "Return stone was not issued");
            ItemStack stone = player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT);
            ItemStack displaced = player.getInventory().getItem(10);
            player.getInventory().setItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT, displaced);
            player.getInventory().setItem(10, stone);
            helper.assertTrue(
                    NetherReturnIntegration.ensureInCentralSlot(player, binding),
                    "Server did not repair a slot swap"
            );
            helper.assertTrue(
                    NetherReturnIntegration.binding(
                            player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT)
                    ).filter(binding::equals).isPresent(),
                    "Return stone did not return to slot 4"
            );
            helper.assertTrue(player.getInventory().getItem(10).is(Items.COBBLESTONE), "Swapped item was lost");

            stone = player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT);
            player.getInventory().setItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT, new ItemStack(Items.DIRT));
            player.containerMenu.setCarried(stone);
            NetherReturnIntegration.ensureInCentralSlot(player, binding);
            helper.assertTrue(player.containerMenu.getCarried().is(Items.DIRT), "Cursor swap item was lost");

            stone = player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT);
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.getInventory().setItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT, new ItemStack(Items.GOLD_INGOT));
            player.getInventory().setItem(40, stone);
            NetherReturnIntegration.ensureInCentralSlot(player, binding);
            helper.assertTrue(player.getInventory().getItem(40).is(Items.GOLD_INGOT), "Offhand swap item was lost");

            stone = player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT);
            SimpleContainer chest = new SimpleContainer(27);
            chest.setItem(0, stone);
            player.getInventory().setItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT, new ItemStack(Items.EMERALD));
            player.containerMenu = ChestMenu.threeRows(83, player.getInventory(), chest);
            NetherReturnIntegration.ensureInCentralSlot(player, binding);
            helper.assertTrue(chest.getItem(0).is(Items.EMERALD), "Container swap item was lost");
            helper.assertTrue(
                    NetherReturnIntegration.binding(
                            player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT)
                    ).filter(binding::equals).isPresent(),
                    "Return stone did not return from a container to slot 4"
            );

            NetherReturnIntegration.removeForPlayer(player);
            helper.assertTrue(
                    player.getInventory().getItem(NetherReturnIntegration.CENTRAL_HOTBAR_SLOT).isEmpty(),
                    "Return stone survived session exit"
            );
        });
    }

    @GameTest(template = "empty")
    public static void creativeTabContainsEveryKingdomsItem(GameTestHelper helper) {
        Set<net.minecraft.world.item.Item> registered = BuiltInRegistries.ITEM.stream()
                .filter(item -> KalFactions.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace()))
                .collect(Collectors.toSet());
        Set<net.minecraft.world.item.Item> displayed = Set.copyOf(ModCreativeTabs.creativeItems());
        helper.assertTrue(
                displayed.equals(registered),
                "Kingdoms creative tab differs from registered items; missing="
                        + difference(registered, displayed) + ", unknown=" + difference(displayed, registered)
        );
        helper.assertTrue(displayed.contains(ModItems.NETHER_RETURN.get()), "Return stone is missing from creative tab");
        ItemStack creativeCopy = new ItemStack(ModItems.NETHER_RETURN.get());
        helper.assertTrue(
                NetherReturnIntegration.binding(creativeCopy).isEmpty(),
                "Creative return stone was incorrectly marked as a server session instance"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherWipeRequiresDimensionKeyAndExecutesOnceOnStartup(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            helper.assertTrue(!manager.isWipePending(Level.NETHER), "Fresh server scheduled an automatic wipe");
            helper.assertTrue(
                    !manager.setWipePending(Level.NETHER, true),
                    "Generic command path scheduled a Nether wipe"
            );
            helper.assertTrue(manager.requestNetherWipeFromDimensionKey(), "Dimension Key did not schedule wipe");
            DimensionControlManager restarted = DimensionControlManager.forTesting(path);
            helper.assertTrue(restarted.isWipePending(Level.NETHER), "Pending wipe was not persisted");
            helper.assertTrue(restarted.cancelNetherWipeFromDimensionKey(), "Pending wipe could not be cancelled");
            helper.assertTrue(!DimensionControlManager.forTesting(path).isWipePending(Level.NETHER),
                    "Cancelled wipe reappeared after restart");
            helper.assertTrue(restarted.requestNetherWipeFromDimensionKey(), "Wipe could not be requested again");
            helper.assertTrue(restarted.completePendingWipe(Level.NETHER, 123L), "Pending wipe did not execute");
            helper.assertTrue(!restarted.completePendingWipe(Level.NETHER, 123L), "Pending wipe executed twice");
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

    private static ServerPlayer createPlayer(GameTestHelper helper, String prefix) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), prefix + UUID.randomUUID().toString().substring(0, 6)
        );
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        return new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()
        );
    }

    private static Set<net.minecraft.world.item.Item> difference(
            Set<net.minecraft.world.item.Item> left,
            Set<net.minecraft.world.item.Item> right
    ) {
        java.util.HashSet<net.minecraft.world.item.Item> difference = new java.util.HashSet<>(left);
        difference.removeAll(right);
        return difference;
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
