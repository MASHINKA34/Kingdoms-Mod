package com.geydev.kalfactions.scout;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.gametest.RegressionPlayers;
import com.geydev.kalfactions.integration.xaero.archive.XaeroArchiveStore;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScoutLifecycleGameTests {
    @GameTest(template = "empty", batch = "scout_lifecycle")
    public static void invalidCoordinatesAreRejectedAndExpeditingSurvivesReload(GameTestHelper helper) {
        for (int coordinate : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE,
                -ScoutPayloads.MAX_CHUNK_COORDINATE - 1, ScoutPayloads.MAX_CHUNK_COORDINATE + 1}) {
            for (boolean xAxis : new boolean[]{true, false}) {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                try {
                    var payload = new ScoutPayloads.C2SScoutOrder(0, helper.getLevel().dimension().location(),
                            xAxis ? coordinate : 0, xAxis ? 0 : coordinate);
                    ScoutPayloads.C2SScoutOrder.STREAM_CODEC.encode(buffer, payload);
                    boolean rejected = false;
                    try {
                        ScoutPayloads.C2SScoutOrder.STREAM_CODEC.decode(buffer);
                    } catch (DecoderException exception) {
                        rejected = true;
                    }
                    helper.assertTrue(rejected, "invalid coordinate must not wrap into an accepted chunk");
                } finally {
                    buffer.release();
                }
            }
        }
        ScoutOrder order = new ScoutOrder(UUID.randomUUID(), ScoutPackage.SMALL, helper.getLevel().dimension(),
                0, 0, 3, 1000L, 60000L, 100L, UUID.randomUUID());
        helper.assertFalse(order.timeElapsed(2000L), "ordinary order still waits for its deadline");
        order.expedite();
        ScoutOrder loaded = ScoutOrder.load(order.save(), helper.getLevel().registryAccess());
        helper.assertTrue(loaded != null && loaded.timeElapsed(2000L), "expediting survives a restart");
        helper.assertFalse(loaded.scanned(), "expediting does not fabricate an unperformed survey");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scout_lifecycle")
    public static void progressChangesAreSentWithoutRepeatedIdenticalStates(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var fixture = RegressionPlayers.create(level, pos, 0);
        var factions = new FactionManager();
        helper.assertTrue(factions.createFaction(fixture.player().getUUID(), "Scouts", ClaimKey.of(level, pos), 1)
                .successful(), "scout faction created");
        UUID faction = factions.getFactionIdForMember(fixture.player().getUUID()).orElseThrow();
        var manager = new ScoutManager();
        ScoutOrder order = new ScoutOrder(UUID.randomUUID(), ScoutPackage.SMALL, level.dimension(),
                0, 0, 2, System.currentTimeMillis(), 60000L, 0L, null);
        manager.startOrder(faction, order);
        var originalFactions = FactionManager.get(level);
        var originalScouts = ScoutManager.get(level.getServer());
        var storage = level.getServer().overworld().getDataStorage();
        storage.set(FactionManager.DATA_NAME, factions);
        storage.set(ScoutManager.DATA_NAME, manager);
        try {
            ScoutService.syncStateIfChanged(fixture.player());
            ScoutService.syncStateIfChanged(fixture.player());
            order.setCursor(2);
            ScoutService.syncStateIfChanged(fixture.player());
            ScoutService.syncStateIfChanged(fixture.player());
            order.markScanned(List.of("0_0.zip"));
            ScoutService.syncStateIfChanged(fixture.player());
            var progress = fixture.packets().stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(ClientboundCustomPayloadPacket.class::cast).map(ClientboundCustomPayloadPacket::payload)
                    .filter(ScoutPayloads.S2CScoutState.class::isInstance).map(ScoutPayloads.S2CScoutState.class::cast)
                    .map(ScoutPayloads.S2CScoutState::progressPercent).toList();
            helper.assertValueEqual(progress, List.of(0, 50, 100), "client gets each new progress value once");
        } finally {
            ScoutService.onLogout(fixture.player().getUUID());
            storage.set(FactionManager.DATA_NAME, originalFactions);
            storage.set(ScoutManager.DATA_NAME, originalScouts);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "scout_lifecycle", timeoutTicks = 600)
    public static void cancelledStagingCannotAffectReplacementAndQueuedOrders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(helper.absolutePos(new BlockPos(1, 2, 1)));
        ScoutManager manager = new ScoutManager();
        ScoutRuntime runtime = new ScoutRuntime(level.getServer());
        UUID factionId = UUID.randomUUID();
        ScoutOrder cancelled = order(level, chunk);
        manager.startOrder(factionId, cancelled);
        List<UUID> owners = new ArrayList<>(List.of(factionId, UUID.randomUUID(), UUID.randomUUID()));
        List<ScoutOrder> replacements = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(runtime.tick(manager, 1).isEmpty(), "initial order has no delivery yet");
                    helper.assertTrue(cancelled.cursor() == 1 && !cancelled.scanned(), "waiting for staging");
                })
                .thenExecute(() -> {
                    helper.assertTrue(runtime.cancel(factionId, cancelled), "staging can be cancelled");
                    manager.removeOrder(factionId);
                    for (UUID owner : owners) {
                        ScoutOrder replacement = order(level, chunk);
                        replacements.add(replacement);
                        helper.assertTrue(manager.startOrder(owner, replacement), "new order accepted");
                    }
                })
                .thenWaitUntil(() -> {
                    for (ScoutRuntime.Result result : runtime.tick(manager, 1)) {
                        helper.assertTrue(result.successful(), "queued survey delivered successfully");
                        helper.assertTrue(manager.activeOrder(result.factionId()).orElse(null) == result.order(),
                                "completion only belongs to the current order");
                        result.order().markDelivered();
                    }
                    helper.assertTrue(replacements.stream().allMatch(ScoutOrder::delivered),
                            "waiting for all queued surveys");
                })
                .thenExecute(() -> {
                    try {
                        for (UUID owner : owners) {
                            var archive = XaeroArchiveStore.location(level.getServer(), owner, level.dimension().location());
                            helper.assertValueEqual(XaeroArchiveStore.load(archive).regions().stream()
                                    .mapToInt(region -> region.tileCount()).sum(), 1, "one tile delivered per order");
                        }
                        var staging = level.getServer().getWorldPath(LevelResource.ROOT)
                                .resolve("kingdoms").resolve("scout_staging");
                        helper.assertFalse(Files.exists(staging.resolve(cancelled.id().toString())),
                                "cancelled staging removed after its writer finished");
                        for (ScoutOrder replacement : replacements) {
                            helper.assertTrue(Files.isDirectory(staging.resolve(replacement.id().toString())),
                                    "delivered staging remains recoverable until saved state is loaded on restart");
                        }
                    } catch (IOException exception) {
                        helper.fail("Cannot read delivered scout archive: " + exception);
                    } finally {
                        runtime.close();
                        try {
                            Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                            for (UUID owner : owners) {
                                deleteTestFiles(worldRoot, XaeroArchiveStore.location(
                                        level.getServer(), owner, level.dimension().location()).root());
                            }
                            for (ScoutOrder replacement : replacements) {
                                deleteTestFiles(worldRoot, worldRoot.resolve("kingdoms").resolve("scout_staging")
                                        .resolve(replacement.id().toString()));
                            }
                        } catch (IOException exception) {
                            helper.fail("Cannot clean scout lifecycle test files: " + exception);
                        }
                    }
                })
                .thenSucceed();
    }

    private static ScoutOrder order(ServerLevel level, ChunkPos chunk) {
        return new ScoutOrder(UUID.randomUUID(), ScoutPackage.SMALL, level.dimension(), chunk.x, chunk.z,
                1, System.currentTimeMillis(), 0L, 0L, null);
    }

    private static void deleteTestFiles(Path worldRoot, Path root) throws IOException {
        if (!root.toAbsolutePath().normalize().startsWith(worldRoot.resolve("kingdoms")) || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private ScoutLifecycleGameTests() {
    }
}
