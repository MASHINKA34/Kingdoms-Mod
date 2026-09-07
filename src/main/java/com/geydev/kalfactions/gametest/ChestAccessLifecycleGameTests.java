package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.chest.ChestAccess;
import com.geydev.kalfactions.chest.ChestAccessCleanup;
import com.geydev.kalfactions.chest.ChestAccessMode;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChestAccessLifecycleGameTests {
    @GameTest(template = "empty", batch = "chest_access_lifecycle")
    public static void breakingAProtectedChestForgetsItsRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer player = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction owner = faction(manager, player.getUUID(), ClaimKey.of(level, pos));
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            protect(manager, level, pos, owner.id(), player.getUUID());
            helper.assertTrue(
                    manager.getChestAccess(ChestAccess.Key.of(level, pos)).isPresent(),
                    "the rule exists before the break");

            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            ChestAccessCleanup.forgetIfContainerIsGone(level, pos);

            helper.assertTrue(
                    manager.getChestAccess(ChestAccess.Key.of(level, pos)).isEmpty(),
                    "breaking the chest forgets its access rule");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_access_lifecycle")
    public static void aCancelledBreakKeepsTheRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer player = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction owner = faction(manager, player.getUUID(), ClaimKey.of(level, pos));
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            protect(manager, level, pos, owner.id(), player.getUUID());

            ChestAccessCleanup.forgetIfContainerIsGone(level, pos);

            helper.assertTrue(
                    manager.getChestAccess(ChestAccess.Key.of(level, pos)).isPresent(),
                    "a chest that survived the break keeps its rule");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_access_lifecycle")
    public static void placingAChestDoesNotInheritAnOldRule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer player = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction owner = faction(manager, player.getUUID(), ClaimKey.of(level, pos));
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            protect(manager, level, pos, owner.id(), player.getUUID());
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ChestAccessCleanup.onBlockPlace(new BlockEvent.EntityPlaceEvent(
                    snapshot, level.getBlockState(pos.below()), player));

            helper.assertTrue(
                    manager.getChestAccess(ChestAccess.Key.of(level, pos)).isEmpty(),
                    "a freshly placed chest starts without the old rule");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "chest_access_lifecycle")
    public static void aRuleWithoutItsContainerHealsOnRead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer player = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction owner = faction(manager, player.getUUID(), ClaimKey.of(level, pos));
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            protect(manager, level, pos, owner.id(), player.getUUID());
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());

            manager.canAccessContainer(player.getUUID(), level, pos);

            helper.assertTrue(
                    manager.getChestAccess(ChestAccess.Key.of(level, pos)).isEmpty(),
                    "a rule whose container is gone is dropped on the next read");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private static void protect(
            FactionManager manager,
            ServerLevel level,
            BlockPos pos,
            UUID factionId,
            UUID ownerId
    ) {
        FactionManager.OperationResult result = manager.setChestAccess(new ChestAccess(
                ChestAccess.Key.of(level, pos), factionId, ownerId, ChestAccessMode.PERSONAL));
        if (!result.successful()) {
            throw new IllegalStateException("Cannot protect the test chest: " + result);
        }
    }

    private static Faction faction(FactionManager manager, UUID owner, ClaimKey claim) {
        var created = manager.createFaction(owner, "Vaults", 0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"), Set.of(), false, claim, 1);
        if (!created.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + created);
        }
        return manager.getFactionForMember(owner).orElseThrow();
    }

    private ChestAccessLifecycleGameTests() {
    }
}
