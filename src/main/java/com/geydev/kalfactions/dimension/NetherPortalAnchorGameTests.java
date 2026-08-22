package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetherPortalAnchorGameTests {
    private static final BlockPos FRAME_INTERIOR = new BlockPos(3, 93, 3);
    private static final BlockPos LONE_ANCHOR = new BlockPos(9, 93, 9);

    @GameTest(template = "empty")
    public static void igniterLightsTheAnchoredFrameAndIsConsumed(GameTestHelper helper) {
        BlockPos anchor = buildAnchoredFrame(helper);
        DimensionControlManager control = resetControl(helper);
        ServerPlayer player = createPlayer(helper);
        ItemStack igniter = new ItemStack(ModItems.NETHER_IGNITER.get(), 2);

        NetherPortalIgnition.igniteWithItem(player, helper.absolutePos(anchor), igniter);

        helper.assertTrue(igniter.getCount() == 1, "Ignition did not consume exactly one igniter");
        helper.assertTrue(
                helper.getBlockState(FRAME_INTERIOR).is(Blocks.NETHER_PORTAL),
                "The anchored frame stayed empty after ignition"
        );
        helper.assertTrue(control.isNetherPortalCharged(Instant.now()), "Lit portal was not charged");
        helper.assertTrue(control.netherPortal().isPresent(), "Lit portal was not registered");

        NetherPortalIgnition.Result second = NetherPortalIgnition.ignite(
                helper.getLevel(), helper.absolutePos(anchor), "Tester", Instant.now()
        );
        helper.assertTrue(
                second.failure() == NetherPortalIgnition.Failure.ALREADY_LIT,
                "A burning portal accepted a second charge"
        );

        clear(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void flintAndSteelCannotLightTheAnchoredFrame(GameTestHelper helper) {
        buildAnchoredFrame(helper);
        DimensionControlManager control = resetControl(helper);
        helper.assertTrue(
                PortalShape.findEmptyPortalShape(
                        helper.getLevel(), helper.absolutePos(FRAME_INTERIOR), Direction.Axis.X
                ).isPresent(),
                "The anchor was not accepted as part of a vanilla portal frame"
        );

        helper.setBlock(FRAME_INTERIOR, Blocks.FIRE);

        helper.assertTrue(
                !helper.getBlockState(FRAME_INTERIOR).is(Blocks.NETHER_PORTAL),
                "Vanilla fire opened the spawn portal"
        );
        helper.assertTrue(
                !control.isNetherPortalCharged(Instant.now()),
                "Vanilla fire charged the spawn portal"
        );

        helper.setBlock(FRAME_INTERIOR, Blocks.AIR);
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anchorOutsideAFrameCannotBeLit(GameTestHelper helper) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    helper.setBlock(LONE_ANCHOR.offset(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(LONE_ANCHOR, ModBlocks.NETHER_PORTAL_ANCHOR.get());
        DimensionControlManager control = resetControl(helper);

        NetherPortalIgnition.Result result = NetherPortalIgnition.ignite(
                helper.getLevel(), helper.absolutePos(LONE_ANCHOR), "Tester", Instant.now()
        );

        helper.assertTrue(
                result.failure() == NetherPortalIgnition.Failure.NO_FRAME,
                "An anchor without a frame was accepted"
        );
        helper.assertTrue(!control.isNetherPortalCharged(Instant.now()), "A frameless anchor charged the portal");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void expiredPortalGoesOutEvacuatesAndKeepsTheAnchor(GameTestHelper helper) {
        BlockPos anchor = buildAnchoredFrame(helper);
        DimensionControlManager control = resetControl(helper);
        Instant lit = Instant.now();

        NetherPortalIgnition.Result result = NetherPortalIgnition.ignite(
                helper.getLevel(), helper.absolutePos(anchor), "Tester", lit
        );
        helper.assertTrue(result.ignited(), "The anchored frame refused the igniter");
        Instant expiry = result.charge().expiresAt();
        helper.assertTrue(control.isNetherPortalCharged(expiry.minusSeconds(1)), "The charge expired early");

        NetherPortalIgnition.tick(helper.getLevel().getServer(), expiry.minusSeconds(1));
        helper.assertTrue(
                helper.getBlockState(FRAME_INTERIOR).is(Blocks.NETHER_PORTAL),
                "A live portal was closed before its deadline"
        );

        NetherPortalIgnition.tick(helper.getLevel().getServer(), expiry.plusSeconds(1));
        helper.assertTrue(
                !helper.getBlockState(FRAME_INTERIOR).is(Blocks.NETHER_PORTAL),
                "An expired portal kept its portal blocks"
        );
        helper.assertTrue(!control.isNetherPortalCharged(expiry.plusSeconds(1)), "An expired portal stayed charged");
        helper.assertTrue(control.netherPortal().isEmpty(), "An expired portal stayed registered");
        helper.assertTrue(
                helper.getBlockState(anchor).is(ModBlocks.NETHER_PORTAL_ANCHOR.get()),
                "The anchor was destroyed together with the portal"
        );
        helper.assertTrue(
                DimensionControlEvents.evacuateNether(
                        helper.getLevel().getServer(), "kingdoms.nether.portal.expired"
                ) == 0,
                "Nether evacuation reported players that are not there"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unlitPortalBlocksEntryInsideTheEveningWindow(GameTestHelper helper) {
        isolated(helper, path -> {
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            Instant evening = ZonedDateTime.of(
                    LocalDate.of(2026, 8, 22), LocalTime.of(18, 30), NetherSchedulePolicy.MOSCOW
            ).toInstant();

            helper.assertTrue(NetherSchedulePolicy.isOpen(evening), "18:30 Moscow fell outside the schedule window");
            helper.assertTrue(
                    !manager.isNetherPortalCharged(evening),
                    "An unlit portal allowed entry during the evening window"
            );

            manager.igniteNetherPortal(evening, Duration.ofHours(48), "Operator", new BlockPos(4, 70, 8));
            helper.assertTrue(manager.isNetherPortalCharged(evening), "A lit portal was reported unlit");
            helper.assertTrue(
                    !manager.isNetherPortalCharged(evening.plus(Duration.ofHours(49))),
                    "The portal outlived its configured lifetime"
            );
        });
    }

    @GameTest(template = "empty")
    public static void portalChargeSurvivesRestartAndSkipsMissedHours(GameTestHelper helper) {
        isolated(helper, path -> {
            Instant lit = Instant.parse("2026-08-22T10:00:00Z");
            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            manager.igniteNetherPortal(lit, Duration.ofHours(48), "Operator", new BlockPos(4, 70, 8));

            DimensionControlManager restarted = DimensionControlManager.forTesting(path);
            helper.assertTrue(
                    restarted.isNetherPortalCharged(lit.plus(Duration.ofHours(47))),
                    "A live charge was lost across a restart"
            );
            helper.assertTrue(
                    !restarted.isNetherPortalCharged(lit.plus(Duration.ofDays(7))),
                    "A portal survived a downtime longer than its lifetime"
            );
            helper.assertTrue(
                    restarted.netherPortalAnchor().orElseThrow().equals(new BlockPos(4, 70, 8)),
                    "The anchor position was lost across a restart"
            );
        });
    }

    @GameTest(template = "empty")
    public static void existingWorldsStartUnlitButKeepTheirRegistration(GameTestHelper helper) {
        isolated(helper, path -> {
            try {
                Files.writeString(path, """
                        {
                          "formatVersion": 4,
                          "netherPortal": {"minX": 1, "minY": 2, "minZ": 3, "maxX": 2, "maxY": 4, "maxZ": 3},
                          "netherPortalChargedUntil": 9999999999999,
                          "netherPortalIgnitedBy": "Legacy"
                        }
                        """, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }

            DimensionControlManager manager = DimensionControlManager.forTesting(path);
            helper.assertTrue(
                    !manager.isNetherPortalCharged(Instant.now()),
                    "A world upgraded from the old format started with a lit portal"
            );
            helper.assertTrue(manager.netherPortalCharge().isEmpty(), "A legacy charge survived the upgrade");
            helper.assertTrue(manager.netherPortal().isPresent(), "The legacy portal registration was dropped");
        });
    }

    private static BlockPos buildAnchoredFrame(GameTestHelper helper) {
        for (int x = -1; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                boolean frame = x < 0 || x > 1 || y < 0 || y > 2;
                helper.setBlock(FRAME_INTERIOR.offset(x, y, 0), frame ? Blocks.OBSIDIAN : Blocks.AIR);
            }
        }
        BlockPos anchor = FRAME_INTERIOR.below();
        helper.setBlock(anchor, ModBlocks.NETHER_PORTAL_ANCHOR.get());
        return anchor;
    }

    private static DimensionControlManager resetControl(GameTestHelper helper) {
        DimensionControlManager control = DimensionControlManager.get(helper.getLevel().getServer());
        control.clearNetherPortalCharge();
        control.clearNetherPortal();
        return control;
    }

    private static void clear(GameTestHelper helper) {
        DimensionControlManager control = resetControl(helper);
        NetherPortalRegistration.clearConnectedPortal(helper.getLevel(), helper.absolutePos(FRAME_INTERIOR));
        control.clearNetherPortalCharge();
    }

    private static ServerPlayer createPlayer(GameTestHelper helper) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), "portal" + UUID.randomUUID().toString().substring(0, 6)
        );
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        return new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()
        );
    }

    private static void isolated(GameTestHelper helper, Scenario scenario) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("kingdoms-portal-gametest-");
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

    private NetherPortalAnchorGameTests() {
    }
}
