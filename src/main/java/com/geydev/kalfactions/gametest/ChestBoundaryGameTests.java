package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.chest.ChestAccess;
import com.geydev.kalfactions.chest.ChestAccessMode;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.protection.ProtectionHandler;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChestBoundaryGameTests {
    @GameTest(template = "empty", batch = "chest_boundary", timeoutTicks = 400)
    public static void wildernessHalfCannotExposeClaimedInventory(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            faction(fixture.manager, UUID.randomUUID(), fixture.level, fixture.left.east(), 1);
            helper.assertTrue(!fixture.manager.canAccessContainer(fixture.player.getUUID(), fixture.level, fixture.left),
                    "the wilderness half cannot expose a foreign claimed half");
            helper.assertTrue(interact(fixture).isCanceled(), "the actual right-click event is rejected");
            assertExtraction(helper, fixture, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_boundary", timeoutTicks = 400)
    public static void ownHalfCannotChangeOrExposeTheForeignHalf(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            faction(fixture.manager, fixture.player.getUUID(), fixture.level, fixture.left, 1);
            faction(fixture.manager, UUID.randomUUID(), fixture.level, fixture.left.east(), 1);
            helper.assertTrue(!fixture.manager.canAccessContainer(fixture.player.getUUID(), fixture.level, fixture.left),
                    "membership in one half's faction does not grant the other half");
            helper.assertTrue(!AccessTool.setMode(fixture.player, fixture.level, fixture.left,
                    ChestAccessMode.PUBLIC).success(), "the key cannot change a chest spanning foreign claims");
            assertExtraction(helper, fixture, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_boundary", timeoutTicks = 400)
    public static void sharedPublicAndWhitelistRulesStillWork(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Faction owner = faction(fixture.manager, UUID.randomUUID(), fixture.level, fixture.left.east(), 3);
            ChestAccess access = new ChestAccess(ChestAccess.Key.of(fixture.level, fixture.left.east()),
                    owner.id(), owner.ownerId(), ChestAccessMode.PUBLIC);
            helper.assertTrue(fixture.manager.setChestAccess(access).successful(), "the public rule is installed");
            helper.assertTrue(fixture.manager.canAccessContainer(fixture.player.getUUID(), fixture.level, fixture.left),
                    "the public rule applies to both halves in the same faction");
            helper.assertTrue(!interact(fixture).isCanceled(), "public access remains available");
            fixture.manager.setChestAccess(access.withMode(ChestAccessMode.WHITELIST)
                    .withWhitelistedPlayer(fixture.player.getUUID()));
            helper.assertTrue(fixture.manager.canAccessContainer(fixture.player.getUUID(), fixture.level, fixture.left),
                    "the shared whitelist grants both halves");
            fixture.player.containerMenu = ChestMenu.sixRows(1, fixture.player.getInventory(),
                    ChestBlock.getContainer((ChestBlock) Blocks.CHEST, fixture.level.getBlockState(fixture.left),
                            fixture.level, fixture.left, true));
            fixture.manager.setChestAccess(access.withMode(ChestAccessMode.FACTION));
            ProtectionHandler.onPlayerTick(new PlayerTickEvent.Post(fixture.player));
            helper.assertTrue(fixture.player.containerMenu == fixture.player.inventoryMenu,
                    "revoking access closes the combined inventory");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_boundary", timeoutTicks = 400)
    public static void sanctuaryPlotCannotBeOpenedOrDrainedThroughItsNeighbour(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            SanctuaryManager sanctuary = SanctuaryManager.get(fixture.level);
            MarketPlotManager plots = MarketPlotManager.get(fixture.level);
            var storage = fixture.level.getServer().overworld().getDataStorage();
            SanctuaryManager testSanctuary = new SanctuaryManager();
            MarketPlotManager testPlots = new MarketPlotManager();
            testSanctuary.setClaim(ClaimKey.of(fixture.level, fixture.left), true);
            testSanctuary.setClaim(ClaimKey.of(fixture.level, fixture.left.east()), true);
            BlockPos protectedHalf = fixture.left.east();
            testPlots.create(fixture.level.dimension(), new BoundingBox(protectedHalf), 1L).setOwner(UUID.randomUUID(), "Owner");
            storage.set(SanctuaryManager.DATA_NAME, testSanctuary);
            storage.set(MarketPlotManager.DATA_NAME, testPlots);
            try {
                helper.assertTrue(interact(fixture).isCanceled(), "the public sanctuary half cannot open a private plot");
                assertExtraction(helper, fixture, false);
            } finally {
                storage.set(SanctuaryManager.DATA_NAME, sanctuary);
                storage.set(MarketPlotManager.DATA_NAME, plots);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_boundary", timeoutTicks = 400)
    public static void wildernessDoubleChestStillAllowsAccessAndAutomation(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            helper.assertTrue(!interact(fixture).isCanceled(), "normal wilderness chests still open");
            assertExtraction(helper, fixture, true);
        }
        helper.succeed();
    }

    private static void assertExtraction(GameTestHelper helper, Fixture fixture, boolean allowed) {
        var handler = fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, fixture.left, Direction.DOWN);
        helper.assertTrue(handler != null && handler.getSlots() == 54, "the real capability exposes a double chest");
        int extracted = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            extracted += handler.extractItem(slot, 64, false).getCount();
        }
        helper.assertTrue(extracted == (allowed ? 1 : 0), "automation respects every half's boundary");
    }

    private static PlayerInteractEvent.RightClickBlock interact(Fixture fixture) {
        var event = new PlayerInteractEvent.RightClickBlock(fixture.player, InteractionHand.MAIN_HAND, fixture.left,
                new BlockHitResult(Vec3.atCenterOf(fixture.left), Direction.UP, fixture.left, false));
        ProtectionHandler.onRightClickBlock(event);
        return event;
    }

    private static Faction faction(FactionManager manager, UUID owner, ServerLevel level, BlockPos pos, int size) {
        var result = manager.createFaction(owner, "Border" + manager.factions().size(), ClaimKey.of(level, pos), size);
        if (!result.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + result);
        }
        return manager.getFactionForMember(owner).orElseThrow();
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel level;
        private final BlockPos left;
        private final ServerPlayer player;
        private final FactionManager original;
        private final FactionManager manager = new FactionManager();

        private Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            BlockPos spawn = level.getSharedSpawnPos();
            left = new BlockPos(((spawn.getX() + 160) & ~15) - 1, level.getSeaLevel() + 8, spawn.getZ() + 160);
            player = RegressionPlayers.create(level, left, 0).player();
            original = FactionManager.get(level);
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
            level.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                    .setValue(ChestBlock.TYPE, ChestType.LEFT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(left.east(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                    .setValue(ChestBlock.TYPE, ChestType.RIGHT), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ((ChestBlockEntity) level.getBlockEntity(left.east())).setItem(0, new ItemStack(Items.DIAMOND));
            helper.assertTrue(ChestBlock.getContainer((ChestBlock) Blocks.CHEST, level.getBlockState(left),
                    level, left, true) instanceof CompoundContainer, "the two halves are actually connected");
        }

        @Override
        public void close() {
            ProtectionHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            level.removeBlock(left, false);
            level.removeBlock(left.east(), false);
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
    }

    private ChestBoundaryGameTests() {
    }
}
