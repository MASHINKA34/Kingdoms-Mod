package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionForceLoadGameTests {
    @GameTest(template = "empty", batch = "faction_force_load")
    public static void enablingAndDisablingTracksTheAppliedTicket(GameTestHelper helper) {
        run(helper, (server, manager, faction, claim) -> {
            helper.assertTrue(
                    manager.enableForceLoad(server, faction.id(), claim) == FactionManager.ForceLoadResult.ENABLED,
                    "the claim can be force loaded");
            helper.assertTrue(faction.isForceLoaded(claim), "the faction records the force load");
            helper.assertTrue(manager.isForceLoadApplied(claim, faction.id()), "a ticket is applied");

            helper.assertTrue(
                    manager.disableForceLoad(server, faction.id(), claim) == FactionManager.ForceLoadResult.DISABLED,
                    "the force load can be turned off");
            helper.assertFalse(faction.isForceLoaded(claim), "the faction forgets the force load");
            helper.assertTrue(manager.appliedForceLoadCount() == 0, "the ticket is released");
        });
    }

    @GameTest(template = "empty", batch = "faction_force_load")
    public static void aChunkOutsideTheClaimsIsRefused(GameTestHelper helper) {
        run(helper, (server, manager, faction, claim) -> {
            ClaimKey elsewhere = new ClaimKey(claim.dimension(), new ChunkPos(claim.x() + 64, claim.z() + 64));

            helper.assertTrue(
                    manager.enableForceLoad(server, faction.id(), elsewhere)
                            == FactionManager.ForceLoadResult.NOT_OWN_CLAIM,
                    "an unclaimed chunk cannot be force loaded");
            helper.assertTrue(manager.appliedForceLoadCount() == 0, "nothing was applied");
        });
    }

    @GameTest(template = "empty", batch = "faction_force_load")
    public static void suspendingReleasesTheTicketAndKeepsTheChoice(GameTestHelper helper) {
        run(helper, (server, manager, faction, claim) -> {
            manager.enableForceLoad(server, faction.id(), claim);

            manager.setForceLoadsSuspended(faction.id(), true);
            manager.reconcileForceLoads(server);

            helper.assertTrue(manager.appliedForceLoadCount() == 0, "a suspended faction holds no ticket");
            helper.assertTrue(faction.isForceLoaded(claim), "the faction keeps its choice while suspended");

            manager.setForceLoadsSuspended(faction.id(), false);
            manager.reconcileForceLoads(server);

            helper.assertTrue(manager.isForceLoadApplied(claim, faction.id()), "the ticket comes back");
        });
    }

    @GameTest(template = "empty", batch = "faction_force_load")
    public static void losingTheClaimDropsTheForceLoad(GameTestHelper helper) {
        run(helper, (server, manager, faction, claim) -> {
            ClaimKey extra = new ClaimKey(claim.dimension(), new ChunkPos(claim.x() + 1, claim.z()));
            manager.deposit(faction.id(), 1_000_000L);
            helper.assertTrue(manager.claim(faction.id(), extra).successful(), "the faction claims a second chunk");
            manager.enableForceLoad(server, faction.id(), extra);
            helper.assertTrue(manager.isForceLoadApplied(extra, faction.id()), "the ticket is applied");

            helper.assertTrue(manager.unclaim(faction.id(), extra).successful(), "the second chunk is released");
            manager.reconcileForceLoads(server);

            helper.assertFalse(faction.isForceLoaded(extra), "an unclaimed chunk stops being force loaded");
            helper.assertTrue(manager.appliedForceLoadCount() == 0, "its ticket is released");
        });
    }

    @GameTest(template = "empty", batch = "faction_force_load")
    public static void reconcilingTwiceChangesNothing(GameTestHelper helper) {
        run(helper, (server, manager, faction, claim) -> {
            manager.enableForceLoad(server, faction.id(), claim);

            manager.reconcileForceLoads(server);
            manager.reconcileForceLoads(server);

            helper.assertTrue(manager.appliedForceLoadCount() == 1, "reconciling is idempotent");
            helper.assertTrue(manager.isForceLoadApplied(claim, faction.id()), "the same ticket is still applied");
        });
    }

    private interface Scenario {
        void run(MinecraftServer server, FactionManager manager, Faction faction, ClaimKey claim);
    }

    private static void run(GameTestHelper helper, Scenario scenario) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ClaimKey claim = ClaimKey.of(level, helper.absolutePos(new BlockPos(1, 2, 1)));
        FactionManager original = FactionManager.get(server);
        FactionManager manager = new FactionManager();
        Faction faction = create(manager, claim);
        server.overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            manager.reconcileForceLoads(server);
            scenario.run(server, manager, faction, claim);
        } finally {
            for (Faction present : manager.factions()) {
                manager.setForceLoadsSuspended(present.id(), true);
            }
            manager.reconcileForceLoads(server);
            server.overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private static Faction create(FactionManager manager, ClaimKey claim) {
        UUID owner = UUID.randomUUID();
        FactionManager.OperationResult created = manager.createFaction(
                owner,
                "Wardens",
                0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"),
                Set.of(),
                false,
                claim,
                1
        );
        if (!created.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + created);
        }
        return manager.getFactionForMember(owner).orElseThrow();
    }

    private FactionForceLoadGameTests() {
    }
}
