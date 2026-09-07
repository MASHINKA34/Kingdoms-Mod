package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.DrillBlockEntity;
import com.geydev.kalfactions.bonus.BonusHandler;
import com.geydev.kalfactions.bonus.CraftBonusPolicy;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.market.PlotSnapshots;
import com.geydev.kalfactions.protection.ProtectionHandler;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.war.WarChunkSnapshot;
import com.geydev.kalfactions.war.WarManager;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReviewRegressionGameTests {
    @GameTest(template = "empty", batch = "review_resources")
    public static void scorchedResourcesLoadWithOptionalKnifeAbsent(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).is(Tags.Items.TOOLS), "common tools tag loaded");
        helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                "scguns", "create/mechanical_crafting/jr_wristbreaker")).isPresent(), "wristbreaker recipe loaded");
        boolean knifeExists = BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(
                "scguns", "anthralite_knife"));
        helper.assertValueEqual(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(
                "scguns", "anthralite/anthralite_knife_smithing")).isPresent(), knifeExists, "conditional knife recipe");
        var loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(
                Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("scguns", "entities/blunderer")));
        helper.assertTrue(loot != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "blunderer loot loaded");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_bonuses")
    public static void oreBonusKeepsResourcesButNeverDuplicatesOreBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        faction(manager, player.getUUID(), "Miners", ClaimKey.of(level, pos), Set.of(FactionBonus.MINERS));
        double chance = ModConfigSpec.ORE_BONUS_CHANCE.get();
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        ModConfigSpec.ORE_BONUS_CHANCE.set(1.0D);
        try {
            for (var item : List.of(Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE, Items.DIAMOND)) {
                var drops = new ArrayList<>(List.of(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(),
                        new ItemStack(item))));
                BonusHandler.onBonusDrops(new BlockDropsEvent(level, pos, Blocks.DIAMOND_ORE.defaultBlockState(),
                        null, drops, player, new ItemStack(Items.DIAMOND_PICKAXE)));
                helper.assertValueEqual(drops.size(), item == Items.DIAMOND ? 2 : 1, "ore bonus for " + item);
            }
        } finally {
            ModConfigSpec.ORE_BONUS_CHANCE.set(chance);
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_bonuses")
    public static void craftBonusRejectsReversibleConversionsAndKeepsTools(GameTestHelper helper) {
        for (var item : List.of(Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK,
                Items.DIAMOND, Items.DIAMOND_BLOCK, Items.GOLD_INGOT, Items.GOLD_BLOCK)) {
            helper.assertTrue(!CraftBonusPolicy.allows(helper.getLevel(), new ItemStack(item)),
                    "reversible material must not receive a bonus: " + item);
        }
        for (var item : List.of(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.CRAFTING_TABLE, Items.BREAD)) {
            helper.assertTrue(CraftBonusPolicy.allows(helper.getLevel(), new ItemStack(item)),
                    "ordinary crafting keeps its bonus: " + item);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_bonuses")
    public static void craftBonusCannotCopyItemsInsideContainers(GameTestHelper helper) {
        ItemStack shulker = new ItemStack(Items.BLUE_SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
        helper.assertTrue(!CraftBonusPolicy.allows(helper.getLevel(), shulker), "filled shulker cannot be duplicated");
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        helper.assertTrue(CraftBonusPolicy.allows(helper.getLevel(), shulker), "empty shulker retains crafting bonus");
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.DIAMOND, 32))));
        helper.assertTrue(!CraftBonusPolicy.allows(helper.getLevel(), bundle), "filled bundle cannot be duplicated");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_furnace_snapshot")
    public static void warRollbackPreservesLiveFurnaceAfterLitStateChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        FurnaceBlockEntity furnace = (FurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        WarChunkSnapshot snapshot = WarChunkSnapshot.capture(level, new ChunkPos(pos), level.registryAccess());
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(AbstractFurnaceBlock.LIT, true));
        furnace.setItem(0, new ItemStack(Items.RAW_GOLD, 7));
        snapshot.restore(level, new ChunkPos(pos), level.registryAccess());
        helper.assertTrue(level.getBlockEntity(pos) == furnace, "surviving furnace retains its identity");
        helper.assertTrue(furnace.getItem(0).is(Items.RAW_GOLD), "rollback preserves current items");
        helper.assertValueEqual(furnace.getItem(0).getCount(), 7, "rollback preserves current count");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_vault_snapshot")
    public static void warAndPlotSnapshotsNeverRestoreCreateVaultItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BoundingBox box = new BoundingBox(pos);
        level.setBlockAndUpdate(pos, AllBlocks.ITEM_VAULT.getDefaultState());
        ItemVaultBlockEntity vault = (ItemVaultBlockEntity) level.getBlockEntity(pos);
        vault.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 16));
        StructureTemplate legacy = new StructureTemplate();
        legacy.fillFromWorld(level, pos, new Vec3i(1, 1, 1), false, null);
        CompoundTag oldPlot = legacy.save(new CompoundTag());
        CompoundTag newPlot = PlotSnapshots.capture(level, box);
        WarChunkSnapshot war = WarChunkSnapshot.capture(level, new ChunkPos(pos), level.registryAccess());
        helper.assertValueEqual(vault.getInventoryOfBlock().getStackInSlot(0).getCount(), 16,
                "snapshots preserve live vault inventory");
        vault.clearContent();
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        war.restore(level, new ChunkPos(pos), level.registryAccess());
        vault = (ItemVaultBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(vault.getInventoryOfBlock().getStackInSlot(0).isEmpty(), "war restores an empty vault");
        for (CompoundTag snapshot : List.of(oldPlot, newPlot, oldPlot)) {
            helper.assertTrue(PlotSnapshots.restore(level, box, snapshot), "plot restored");
            vault = (ItemVaultBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(vault.getInventoryOfBlock().getStackInSlot(0).isEmpty(), "plot restores an empty vault");
        }
        vault.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.EMERALD, 7));
        helper.assertTrue(PlotSnapshots.restore(level, box, oldPlot), "occupied plot restored");
        int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).stream()
                .filter(item -> item.getItem().is(Items.EMERALD)).mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertValueEqual(dropped, 7, "current vault contents are returned once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_container_access")
    public static void removedMemberLosesOpenChestAndDrillAccess(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction owner = faction(manager, UUID.randomUUID(), "Containers", ClaimKey.of(level, pos), Set.of());
        manager.addMember(owner.id(), player.getUUID());
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            level.setBlockAndUpdate(pos, ModBlocks.DRILL.get().defaultBlockState());
            DrillBlockEntity drill = (DrillBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(drill.stillValid(player), "member can use drill");
            manager.removeMember(owner.id(), player.getUUID());
            helper.assertTrue(!drill.stillValid(player), "removed member cannot use drill");
            manager.addMember(owner.id(), player.getUUID());
            level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
            player.containerMenu = ChestMenu.threeRows(1, player.getInventory(), chest);
            ProtectionHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.containerMenu != player.inventoryMenu, "member keeps open chest");
            manager.removeMember(owner.id(), player.getUUID());
            ProtectionHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "removed member's chest closes");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "review_vault_spoils")
    public static void warSpoilsCountEachPartOfCreateVaultOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, AllBlocks.ITEM_VAULT.getDefaultState());
        ItemVaultBlockEntity first = (ItemVaultBlockEntity) level.getBlockEntity(pos);
        BlockPos secondPos = pos.relative(net.minecraft.core.Direction.get(
                net.minecraft.core.Direction.AxisDirection.POSITIVE, first.getMainConnectionAxis()));
        level.setBlockAndUpdate(secondPos, first.getBlockState());
        ItemVaultBlockEntity second = (ItemVaultBlockEntity) level.getBlockEntity(secondPos);
        ConnectivityHandler.formMulti(first);
        first.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 50));
        second.getInventoryOfBlock().setStackInSlot(0, new ItemStack(Items.DIAMOND, 50));
        var shared = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        int combinedCount = 0;
        for (int slot = 0; slot < shared.getSlots(); slot++) {
            combinedCount += shared.getStackInSlot(slot).getCount();
        }
        helper.assertValueEqual(combinedCount, 100, "vault parts share one aggregate inventory");
        FactionManager original = FactionManager.get(level);
        FactionManager manager = new FactionManager();
        Faction loser = faction(manager, UUID.randomUUID(), "Loser", ClaimKey.of(level, pos), Set.of());
        ClaimKey secondChunk = ClaimKey.of(level, secondPos);
        if (!secondChunk.equals(ClaimKey.of(level, pos))) {
            manager.claim(loser.id(), secondChunk);
        }
        var receiver = RegressionPlayers.create(level, pos, 0).player();
        Faction winner = faction(manager, receiver.getUUID(), "Winner", ClaimKey.of(level, pos.offset(160, 0, 0)), Set.of());
        UUID id = UUID.randomUUID();
        CompoundTag pending = new CompoundTag();
        pending.putUUID("id", id);
        pending.putUUID("winner", winner.id());
        pending.putUUID("loser", loser.id());
        pending.putLong("gameTime", level.getGameTime());
        ListTag entries = new ListTag();
        entries.add(pending);
        CompoundTag saved = new CompoundTag();
        saved.put("pendingSpoils", entries);
        WarManager wars = WarManager.FACTORY.deserializer().apply(saved, level.registryAccess());
        level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, manager);
        try {
            var view = wars.pendingSpoils(level.getServer(), id).orElseThrow();
            helper.assertValueEqual(view.resourceOne(), 30L, "preview shows 30 percent of physical inventory");
            var result = wars.claimSpoils(level.getServer(), receiver, winner.id(), id, WarManager.SpoilsChoice.RESOURCES);
            helper.assertTrue(result == WarManager.ClaimSpoilsResult.SUCCESS, "spoils claimed: " + result);
            int remaining = first.getInventoryOfBlock().getStackInSlot(0).getCount()
                    + second.getInventoryOfBlock().getStackInSlot(0).getCount();
            helper.assertValueEqual(remaining, 70, "only 30 percent confiscated");
            helper.assertValueEqual(receiver.getInventory().countItem(Items.DIAMOND), 30, "winner receives exact share");
        } finally {
            level.getServer().overworld().getDataStorage().set(FactionManager.DATA_NAME, original);
        }
        helper.succeed();
    }

    private static Faction faction(FactionManager manager, UUID owner, String name, ClaimKey claim, Set<FactionBonus> bonuses) {
        var created = manager.createFaction(owner, name, 0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"), bonuses, false, claim, 1);
        if (!created.successful()) {
            throw new IllegalStateException("Cannot create test faction: " + created);
        }
        return manager.getFactionForMember(owner).orElseThrow();
    }

    private ReviewRegressionGameTests() {
    }
}
