package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.market.PlotSnapshots;
import com.geydev.kalfactions.protection.ProtectionHandler;
import com.geydev.kalfactions.war.War;
import com.geydev.kalfactions.war.WarChunkSnapshot;
import com.geydev.kalfactions.war.WarManager;
import com.geydev.kalfactions.war.WarType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SnapshotSafetyGameTests {
    @GameTest(template = "empty", batch = "snapshot_safety")
    public static void plotRestoresOldAndNewChestTemplatesWithoutItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BoundingBox box = new BoundingBox(pos);
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 16));
        StructureTemplate legacy = new StructureTemplate();
        legacy.fillFromWorld(level, pos, new Vec3i(1, 1, 1), false, null);
        CompoundTag oldSnapshot = legacy.save(new CompoundTag());
        CompoundTag newSnapshot = PlotSnapshots.capture(level, box);
        helper.assertValueEqual(chest.getItem(0).getCount(), 16, "capture preserves live inventory");
        chest.clearContent();
        for (CompoundTag snapshot : List.of(oldSnapshot, newSnapshot, oldSnapshot)) {
            helper.assertTrue(PlotSnapshots.restore(level, box, snapshot), "plot restored");
            chest = (ChestBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(chest.isEmpty(), "snapshot must not restore diamonds");
        }
        chest.setItem(0, new ItemStack(Items.EMERALD, 7));
        helper.assertTrue(PlotSnapshots.restore(level, box, oldSnapshot), "occupied plot restored");
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1))
                .stream().filter(item -> item.getItem().is(Items.EMERALD))
                .mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertValueEqual(dropped, 7, "current owner's inventory is returned exactly once");
        helper.assertTrue(((ChestBlockEntity) level.getBlockEntity(pos)).isEmpty(), "restored chest stays empty");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "snapshot_safety")
    public static void snapshotsSurviveTheirNbtRoundTripWithEmptySections(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos sky = new BlockPos(pos.getX(), level.getMaxBuildHeight() - 8, pos.getZ());
        level.setBlockAndUpdate(pos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(sky, Blocks.AIR.defaultBlockState());

        WarChunkSnapshot captured = WarChunkSnapshot.capture(level, new ChunkPos(pos), level.registryAccess());
        WarChunkSnapshot restored = WarChunkSnapshot.load(captured.save());
        try {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(sky, Blocks.OBSIDIAN.defaultBlockState());

            restored.restore(level, new ChunkPos(pos), level.registryAccess());

            helper.assertTrue(
                    level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK),
                    "a solid section is restored after a save and load");
            helper.assertTrue(
                    level.getBlockState(sky).isAir(),
                    "a block built in an air-only section is cleared on rollback");
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(sky, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "snapshot_safety")
    public static void rollbackBlocksDoNotDropForOwnersOrEnvironment(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos newPos = pos.above();
        BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        level.setBlockAndUpdate(pos, diamond);
        level.setBlockAndUpdate(newPos, Blocks.AIR.defaultBlockState());
        WarChunkSnapshot snapshot = WarChunkSnapshot.capture(level, new ChunkPos(pos), level.registryAccess());
        WarManager original = WarManager.get(level);
        CompoundTag saved = original.save(new CompoundTag(), level.registryAccess());
        var player = RegressionPlayers.create(level, pos, 0).player();
        War war = new War(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                WarType.DEFAULT, "regression", War.State.ACTIVE, 0);
        war.putSnapshot(ClaimKey.of(level, pos), snapshot);
        saved.getList("wars", Tag.TAG_COMPOUND).add(war.save());
        WarManager testManager = WarManager.FACTORY.deserializer().apply(saved, level.registryAccess());
        level.getServer().overworld().getDataStorage().set(WarManager.DATA_NAME, testManager);
        try {
            for (var breaker : java.util.Arrays.asList(player, null)) {
                BlockDropsEvent event = drops(level, pos, diamond, breaker);
                ProtectionHandler.onBlockDrops(event);
                helper.assertTrue(event.getDrops().isEmpty(), "restored block must not yield an item");
                helper.assertValueEqual(event.getDroppedExperience(), 0, "restored block must not yield experience");
            }
            var pending = testManager.warForFaction(war.attackerFactionId()).orElseThrow();
            pending.setState(War.State.ENDING);
            BlockDropsEvent ending = drops(level, pos, diamond, player);
            ProtectionHandler.onBlockDrops(ending);
            helper.assertTrue(ending.getDrops().isEmpty(), "pending rollback still prevents duplication");
            BlockDropsEvent placed = drops(level, newPos, diamond, player);
            ProtectionHandler.onBlockDrops(placed);
            helper.assertValueEqual(placed.getDrops().size(), 1, "new block absent from snapshot can drop");
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            snapshot.restore(level, new ChunkPos(pos), level.registryAccess());
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK), "original block restored");
        } finally {
            level.getServer().overworld().getDataStorage().set(WarManager.DATA_NAME, original);
        }
        BlockDropsEvent ordinary = drops(level, pos, diamond, player);
        ProtectionHandler.onBlockDrops(ordinary);
        helper.assertValueEqual(ordinary.getDrops().size(), 1, "ordinary mining keeps drops");
        helper.succeed();
    }

    private static BlockDropsEvent drops(ServerLevel level, BlockPos pos, BlockState state,
                                        net.minecraft.world.entity.Entity breaker) {
        var items = new ArrayList<>(List.of(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(),
                new ItemStack(Items.DIAMOND_BLOCK))));
        BlockDropsEvent event = new BlockDropsEvent(level, pos, state, null, items, breaker, ItemStack.EMPTY);
        event.setDroppedExperience(5);
        return event;
    }

    private SnapshotSafetyGameTests() {
    }
}
