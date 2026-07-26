package com.geydev.kalfactions.quarry;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.menu.QuarryMenu;
import com.geydev.kalfactions.registry.ModItems;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuarryOperationsGameTests {
    @GameTest(template = "empty", batch = "quarry_operations", timeoutTicks = 600)
    public static void authoritativeOperationsCaptureAndDistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer ownerPlayer = createPlayer(level, "qown" + UUID.randomUUID().toString().substring(0, 8));
        ServerPlayer attackerPlayer = createPlayer(level, "qatk" + UUID.randomUUID().toString().substring(0, 8));
        FactionManager factions = FactionManager.get(level);
        Faction ownerFaction = createFaction(level, factions, ownerPlayer, 0);
        Faction attackerFaction = createFaction(level, factions, attackerPlayer, 96);
        QuarryManager manager = QuarryManager.get(level);
        QuarryManager.QuarryView quarry = createNeutralQuarry(level, manager);
        BlockPos core = quarry.core();
        try {
            teleport(ownerPlayer, level, core);
            ownerPlayer.containerMenu = new QuarryMenu(71, ownerPlayer.getInventory(), core);

            QuarryManager.ActionResult withoutSeal = action(ownerPlayer, core, quarry, QuarryPayloads.ACTION_ACTIVATE);
            helper.assertTrue(
                    withoutSeal == QuarryManager.ActionResult.REQUIRES_ACTIVATOR,
                    "activation without seal rejected"
            );
            QuarryService.clearRateLimit(ownerPlayer.getUUID());
            ownerPlayer.getInventory().setItem(0, new ItemStack(ModItems.QUARRY_ACTIVATOR.get()));
            quarry = manager.byCore(core).orElseThrow();
            QuarryManager.ActionResult activated = action(
                    ownerPlayer,
                    core,
                    quarry,
                    QuarryPayloads.ACTION_ACTIVATE
            );
            helper.assertTrue(activated == QuarryManager.ActionResult.SUCCESS, "activation succeeded");
            helper.assertValueEqual(
                    countActivators(ownerPlayer),
                    0,
                    "activation seal consumed after successful mutation"
            );
            long activatedVersion = quarry.stateVersion();
            QuarryManager.ActionResult duplicate = action(
                    ownerPlayer,
                    core,
                    quarry,
                    QuarryPayloads.ACTION_ACTIVATE
            );
            helper.assertTrue(duplicate != QuarryManager.ActionResult.SUCCESS, "duplicate payload rejected");
            QuarryManager.QuarryView activatedView = manager.byCore(core).orElseThrow();
            helper.assertValueEqual(activatedView.stateVersion(), activatedVersion + 1L, "single activation mutation");
            helper.assertValueEqual(activatedView.ownerFactionId(), ownerFaction.id(), "activation owner");
            helper.assertValueEqual(activatedView.level(), 1, "activation level");
            QuarryService.clearRateLimit(ownerPlayer.getUUID());
            QuarryManager.ActionResult stale = action(
                    ownerPlayer,
                    core,
                    quarry,
                    QuarryPayloads.ACTION_UPGRADE
            );
            helper.assertTrue(stale == QuarryManager.ActionResult.STALE_STATE, "stale state rejected");
            QuarryManager.ActionResult negative = QuarryService.performAction(
                    ownerPlayer,
                    new QuarryPayloads.C2SAction(-1, core, -1L, -1)
            );
            helper.assertTrue(negative == QuarryManager.ActionResult.INVALID_REQUEST, "negative envelope rejected");

            QuarryService.clearRateLimit(ownerPlayer.getUUID());
            QuarryManager.ActionResult insufficient = action(
                    ownerPlayer,
                    core,
                    activatedView,
                    QuarryPayloads.ACTION_UPGRADE
            );
            helper.assertTrue(
                    insufficient == QuarryManager.ActionResult.INSUFFICIENT_FUNDS,
                    "insufficient treasury rejected"
            );
            factions.deposit(ownerFaction.id(), 200_000L);
            for (int expectedLevel = 2; expectedLevel <= QuarryManager.MAX_LEVEL; expectedLevel++) {
                QuarryService.clearRateLimit(ownerPlayer.getUUID());
                QuarryManager.QuarryView before = manager.byCore(core).orElseThrow();
                QuarryManager.ActionResult upgraded = action(
                        ownerPlayer,
                        core,
                        before,
                        QuarryPayloads.ACTION_UPGRADE
                );
                helper.assertTrue(upgraded == QuarryManager.ActionResult.SUCCESS, "upgrade " + expectedLevel);
                helper.assertValueEqual(
                        manager.byCore(core).orElseThrow().level(),
                        expectedLevel,
                        "upgraded level"
                );
            }
            QuarryService.clearRateLimit(ownerPlayer.getUUID());
            QuarryManager.ActionResult maximum = action(
                    ownerPlayer,
                    core,
                    manager.byCore(core).orElseThrow(),
                    QuarryPayloads.ACTION_UPGRADE
            );
            helper.assertTrue(maximum == QuarryManager.ActionResult.MAX_LEVEL, "maximum level rejected");

            teleport(attackerPlayer, level, core);
            attackerPlayer.containerMenu = new QuarryMenu(72, attackerPlayer.getInventory(), core);
            QuarryService.clearRateLimit(attackerPlayer.getUUID());
            QuarryManager.ActionResult foreignUpgrade = action(
                    attackerPlayer,
                    core,
                    manager.byCore(core).orElseThrow(),
                    QuarryPayloads.ACTION_UPGRADE
            );
            helper.assertTrue(
                    foreignUpgrade == QuarryManager.ActionResult.WRONG_STATE,
                    "foreign upgrade rejected"
            );
            QuarryService.clearRateLimit(attackerPlayer.getUUID());
            QuarryManager.ActionResult captureStarted = action(
                    attackerPlayer,
                    core,
                    manager.byCore(core).orElseThrow(),
                    QuarryPayloads.ACTION_CAPTURE
            );
            helper.assertTrue(captureStarted == QuarryManager.ActionResult.SUCCESS, "capture started");

            manager.tickCaptureForTest(level.getServer(), core, true, true);
            QuarryManager.QuarryView paused = manager.byCore(core).orElseThrow();
            helper.assertTrue(paused.capturePaused(), "defender pauses capture");
            helper.assertValueEqual(
                    paused.captureTicksRemaining(),
                    QuarryManager.CAPTURE_TICKS,
                    "paused timer unchanged"
            );

            teleportFar(ownerPlayer, level, core);
            manager.tickCaptureForTest(level.getServer(), core, true, false);
            QuarryManager.QuarryView counting = manager.byCore(core).orElseThrow();
            helper.assertTrue(!counting.capturePaused(), "capture resumes without defender");
            helper.assertValueEqual(
                    counting.captureTicksRemaining(),
                    QuarryManager.CAPTURE_TICKS - 20,
                    "capture counts down"
            );
            teleportFar(attackerPlayer, level, core);
            manager.tickCaptureForTest(level.getServer(), core, false, false);
            QuarryManager.QuarryView reset = manager.byCore(core).orElseThrow();
            helper.assertTrue(reset.attackerFactionId() == null, "capture reset when attackers leave");
            helper.assertValueEqual(
                    reset.captureTicksRemaining(),
                    QuarryManager.CAPTURE_TICKS,
                    "reset restores full timer"
            );

            teleport(attackerPlayer, level, core);
            attackerPlayer.containerMenu = new QuarryMenu(73, attackerPlayer.getInventory(), core);
            QuarryService.clearRateLimit(attackerPlayer.getUUID());
            QuarryManager.ActionResult restarted = action(
                    attackerPlayer,
                    core,
                    reset,
                    QuarryPayloads.ACTION_CAPTURE
            );
            helper.assertTrue(restarted == QuarryManager.ActionResult.SUCCESS, "capture restarted");
            for (int tick = 0; tick < QuarryManager.CAPTURE_TICKS / 20; tick++) {
                manager.tickCaptureForTest(level.getServer(), core, true, false);
            }
            QuarryManager.QuarryView captured = manager.byCore(core).orElseThrow();
            helper.assertValueEqual(captured.ownerFactionId(), attackerFaction.id(), "capture transfers owner");
            helper.assertValueEqual(captured.level(), QuarryManager.MAX_LEVEL, "capture preserves level");
            helper.assertTrue(captured.attackerFactionId() == null, "capture state cleared");

            QuarryMenu nearby = new QuarryMenu(74, attackerPlayer.getInventory(), core);
            helper.assertTrue(nearby.stillValid(attackerPlayer), "nearby quarry menu valid");
            teleportFar(attackerPlayer, level, core);
            helper.assertTrue(!nearby.stillValid(attackerPlayer), "distant quarry menu invalid");
        } finally {
            manager.removeByCore(level, core);
            level.setBlock(core, Blocks.AIR.defaultBlockState(), 3);
            factions.disbandFaction(ownerFaction.id());
            factions.disbandFaction(attackerFaction.id());
            QuarryService.clearRateLimit(ownerPlayer.getUUID());
            QuarryService.clearRateLimit(attackerPlayer.getUUID());
        }
        helper.succeed();
    }

    private static QuarryManager.ActionResult action(
            ServerPlayer player,
            BlockPos core,
            QuarryManager.QuarryView state,
            int action
    ) {
        return QuarryService.performAction(player, new QuarryPayloads.C2SAction(
                player.containerMenu.containerId,
                core,
                state.stateVersion(),
                action
        ));
    }

    private static ServerPlayer createPlayer(ServerLevel level, String name) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(),
                level,
                cookie.gameProfile(),
                cookie.clientInformation()
        );
        return player;
    }

    private static Faction createFaction(
            ServerLevel level,
            FactionManager factions,
            ServerPlayer player,
            int zOffset
    ) {
        ChunkPos center = findFactionCenter(level, factions, zOffset);
        FactionManager.OperationResult result = factions.createFaction(
                player.getUUID(),
                "Q" + Integer.toUnsignedString(player.getUUID().hashCode(), 36),
                new ClaimKey(level.dimension(), center),
                1
        );
        if (!result.successful()) {
            throw new IllegalStateException("Unable to create quarry test faction: " + result.status());
        }
        return factions.getFactionById(result.factionId()).orElseThrow();
    }

    private static ChunkPos findFactionCenter(ServerLevel level, FactionManager factions, int zOffset) {
        BlockPos spawn = level.getSharedSpawnPos();
        SanctuaryManager sanctuary = SanctuaryManager.get(level);
        for (int distance = 2_000; distance <= 3_500; distance += 32) {
            ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() + distance, 0, spawn.getZ() + zOffset));
            ClaimKey key = new ClaimKey(level.dimension(), chunk);
            if (factions.getFactionAt(key).isEmpty() && !sanctuary.isSanctuary(key)) {
                return chunk;
            }
        }
        throw new IllegalStateException("No free faction center");
    }

    private static QuarryManager.QuarryView createNeutralQuarry(ServerLevel level, QuarryManager manager) {
        BlockPos spawn = level.getSharedSpawnPos();
        int red = ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt();
        for (int distance = red + 128; distance <= red + 1_800; distance += 16) {
            for (int offset = -3_000; offset <= 3_000; offset += 16) {
                ChunkPos chunk = new ChunkPos(new BlockPos(spawn.getX() - distance, 0, spawn.getZ() + offset));
                if (!QuarryDistribution.isCandidate(
                        level.getSeed() ^ 0x5155415252594C31L,
                        chunk.x,
                        chunk.z,
                        QuarryManager.MINIMUM_SPACING_CHUNKS
                )) {
                    continue;
                }
                level.getChunk(chunk.x, chunk.z);
                QuarryManager.QuarryView existing = manager.all().stream()
                        .filter(view -> new ChunkPos(view.core()).equals(chunk))
                        .findFirst()
                        .orElse(null);
                if (existing != null) {
                    if (existing.ownerFactionId() == null) {
                        return existing;
                    }
                    continue;
                }
                int y = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        chunk.getMiddleBlockX(),
                        chunk.getMiddleBlockZ()
                );
                BlockPos surface = new BlockPos(chunk.getMiddleBlockX(), y, chunk.getMiddleBlockZ());
                level.setBlock(surface, Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(surface.above(), Blocks.AIR.defaultBlockState(), 2);
                if (manager.createAtChunk(level, chunk) == QuarryManager.CreateResult.CREATED) {
                    return manager.all().stream()
                            .filter(view -> new ChunkPos(view.core()).equals(chunk))
                            .findFirst()
                            .orElseThrow();
                }
            }
        }
        throw new IllegalStateException("No neutral quarry test position");
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos core) {
        player.setPos(core.getX() + 0.5D, core.getY() + 1.0D, core.getZ() + 0.5D);
    }

    private static void teleportFar(ServerPlayer player, ServerLevel level, BlockPos core) {
        player.setPos(core.getX() + 64.5D, core.getY() + 1.0D, core.getZ() + 64.5D);
    }

    private static int countActivators(ServerPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.QUARRY_ACTIVATOR.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private QuarryOperationsGameTests() {
    }
}
