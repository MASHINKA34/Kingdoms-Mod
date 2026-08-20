package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.KeyForgeBlockEntity;
import com.geydev.kalfactions.block.KeyForgeType;
import com.geydev.kalfactions.menu.KeyForgeMenu;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InfernalKeyForgeGameTests {
    @GameTest(template = "empty", batch = "infernal_key_forge", timeoutTicks = 200)
    public static void assemblyPersistsClosesAndConsumesExactlyOneSet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);
        fillRecipe(forge);
        tick(level, pos, forge, 37);
        helper.assertValueEqual(forge.progressTicks(), 37, "progress before reload");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        KeyForgeMenu menu = (KeyForgeMenu) forge.createMenu(1, player.getInventory(), player);
        menu.removed(player);
        tick(level, pos, forge, 13);
        menu = (KeyForgeMenu) forge.createMenu(2, player.getInventory(), player);
        helper.assertValueEqual(menu.progress(), 50, "progress after closing and reopening");
        menu.removed(player);

        CompoundTag saved = forge.saveWithoutMetadata(level.registryAccess());
        forge.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        forge = placeForge(level, pos);
        forge.loadCustomOnly(saved, level.registryAccess());

        helper.assertValueEqual(forge.progressTicks(), 50, "progress after reload");
        helper.assertTrue(forge.getItem(0).is(ModItems.INFERNAL_KEY_BOW_FRAGMENT.get()), "bow after reload");
        helper.assertTrue(forge.getItem(1).is(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()), "shaft after reload");
        helper.assertTrue(forge.getItem(2).is(ModItems.INFERNAL_KEY_BIT_FRAGMENT.get()), "bit after reload");

        tick(level, pos, forge, KeyForgeBlockEntity.ASSEMBLY_TICKS - 50);
        helper.assertTrue(forge.getItem(0).isEmpty(), "bow consumed");
        helper.assertTrue(forge.getItem(1).isEmpty(), "shaft consumed");
        helper.assertTrue(forge.getItem(2).isEmpty(), "bit consumed");
        helper.assertTrue(forge.getItem(3).is(ModItems.INFERNAL_KEY.get()), "assembled key");
        helper.assertValueEqual(forge.getItem(3).getCount(), 1, "assembled key count");
        helper.assertValueEqual(forge.progressTicks(), 0, "progress after assembly");
        clearForge(level, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "infernal_key_forge", timeoutTicks = 200)
    public static void exactSlotsRejectEverythingElseResetAndPause(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);

        for (int slot = 0; slot < 3; slot++) {
            helper.assertTrue(!forge.canPlaceItem(slot, new ItemStack(Items.STONE)), "stone accepted in slot " + slot);
            for (Item foreignPart : foreignParts()) {
                helper.assertTrue(
                        !forge.canPlaceItem(slot, new ItemStack(foreignPart)),
                        "foreign forge part accepted in slot " + slot
                );
            }
        }
        helper.assertTrue(forge.canPlaceItem(0, new ItemStack(ModItems.INFERNAL_KEY_BOW_FRAGMENT.get())), "bow rejected");
        helper.assertTrue(forge.canPlaceItem(1, new ItemStack(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get())), "shaft rejected");
        helper.assertTrue(forge.canPlaceItem(2, new ItemStack(ModItems.INFERNAL_KEY_BIT_FRAGMENT.get())), "bit rejected");
        helper.assertTrue(!forge.canPlaceItem(3, new ItemStack(ModItems.INFERNAL_KEY.get())), "result accepted insertion");
        forge.setItem(0, new ItemStack(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()));
        forge.setItem(1, new ItemStack(ModItems.INFERNAL_KEY_BIT_FRAGMENT.get()));
        forge.setItem(2, new ItemStack(ModItems.INFERNAL_KEY_BOW_FRAGMENT.get()));
        helper.assertTrue(forge.getItem(0).isEmpty(), "shaft inserted into bow slot");
        helper.assertTrue(forge.getItem(1).isEmpty(), "bit inserted into shaft slot");
        helper.assertTrue(forge.getItem(2).isEmpty(), "bow inserted into bit slot");
        forge.setItem(3, new ItemStack(ModItems.INFERNAL_KEY.get()));
        helper.assertTrue(forge.getItem(3).isEmpty(), "manual result insertion succeeded");
        forge.setItem(0, new ItemStack(ModItems.INFERNAL_KEY_BOW_FRAGMENT.get(), 64));
        helper.assertValueEqual(forge.getItem(0).getCount(), 1, "bow input stack limit");
        forge.clearContent();

        fillRecipe(forge);
        tick(level, pos, forge, 25);
        forge.removeItem(1, 1);
        helper.assertValueEqual(forge.progressTicks(), 0, "progress after removing a fragment");
        forge.setItem(1, new ItemStack(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()));
        tick(level, pos, forge, KeyForgeBlockEntity.ASSEMBLY_TICKS);
        helper.assertTrue(forge.getItem(3).is(ModItems.INFERNAL_KEY.get()), "first result missing");

        fillRecipe(forge);
        tick(level, pos, forge, 40);
        helper.assertValueEqual(forge.progressTicks(), 0, "occupied output did not pause");
        helper.assertValueEqual(forge.getItem(0).getCount(), 1, "bow consumed while paused");
        helper.assertValueEqual(forge.getItem(1).getCount(), 1, "shaft consumed while paused");
        helper.assertValueEqual(forge.getItem(2).getCount(), 1, "bit consumed while paused");
        forge.removeItem(3, 1);
        tick(level, pos, forge, 1);
        helper.assertValueEqual(forge.progressTicks(), 1, "assembly did not resume after output cleared");
        clearForge(level, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "infernal_key_forge", timeoutTicks = 200)
    public static void shiftClickRoutesOnlyExactPartsAndCannotDuplicateResult(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        player.getInventory().setItem(9, new ItemStack(ModItems.INFERNAL_KEY_BOW_FRAGMENT.get(), 2));
        player.getInventory().setItem(10, new ItemStack(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()));
        player.getInventory().setItem(11, new ItemStack(ModItems.INFERNAL_KEY_BIT_FRAGMENT.get()));
        player.getInventory().setItem(12, new ItemStack(ModItems.SCULK_KEY_BOW_FRAGMENT.get()));
        player.getInventory().setItem(13, new ItemStack(Items.STONE));

        KeyForgeMenu menu = (KeyForgeMenu) forge.createMenu(1, player.getInventory(), player);
        helper.assertTrue(!menu.getSlot(KeyForgeBlockEntity.RESULT_SLOT).mayPlace(
                new ItemStack(ModItems.INFERNAL_KEY.get())
        ), "result slot accepts placement");
        helper.assertTrue(!menu.quickMoveStack(player, 4).isEmpty(), "bow shift-click failed");
        helper.assertTrue(!menu.quickMoveStack(player, 5).isEmpty(), "shaft shift-click failed");
        helper.assertTrue(!menu.quickMoveStack(player, 6).isEmpty(), "bit shift-click failed");
        helper.assertTrue(menu.quickMoveStack(player, 7).isEmpty(), "foreign part shift-click succeeded");
        helper.assertTrue(menu.quickMoveStack(player, 8).isEmpty(), "stone shift-click succeeded");
        helper.assertValueEqual(forge.getItem(0).getCount(), 1, "bow input stack size");
        helper.assertValueEqual(player.getInventory().getItem(9).getCount(), 1, "bow remainder");
        helper.assertTrue(forge.getItem(1).is(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()), "shaft routing");
        helper.assertTrue(forge.getItem(2).is(ModItems.INFERNAL_KEY_BIT_FRAGMENT.get()), "bit routing");

        tick(level, pos, forge, KeyForgeBlockEntity.ASSEMBLY_TICKS);
        helper.assertTrue(!menu.quickMoveStack(player, 3).isEmpty(), "result shift-click failed");
        helper.assertValueEqual(count(player, ModItems.INFERNAL_KEY.get()), 1, "key count after shift-click");
        helper.assertTrue(menu.quickMoveStack(player, 3).isEmpty(), "empty result duplicated a key");
        helper.assertValueEqual(count(player, ModItems.INFERNAL_KEY.get()), 1, "key count after second shift-click");
        menu.removed(player);
        clearForge(level, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "infernal_key_forge", timeoutTicks = 200)
    public static void breakingForgeDropsEveryStoredItemOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);
        fillRecipe(forge);
        level.destroyBlock(pos, false);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D));
        helper.assertValueEqual(countDrops(drops, ModItems.INFERNAL_KEY_BOW_FRAGMENT.get()), 1, "dropped bows");
        helper.assertValueEqual(countDrops(drops, ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()), 1, "dropped shafts");
        helper.assertValueEqual(countDrops(drops, ModItems.INFERNAL_KEY_BIT_FRAGMENT.get()), 1, "dropped bits");
        helper.assertTrue(level.getBlockEntity(pos) == null, "forge block entity survived destruction");
        drops.forEach(Entity::discard);
        helper.succeed();
    }

    private static KeyForgeBlockEntity placeForge(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.INFERNAL_KEY_FORGE.get().defaultBlockState(), 3);
        return (KeyForgeBlockEntity) level.getBlockEntity(pos);
    }

    private static void fillRecipe(KeyForgeBlockEntity forge) {
        forge.setItem(0, new ItemStack(ModItems.INFERNAL_KEY_BOW_FRAGMENT.get()));
        forge.setItem(1, new ItemStack(ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get()));
        forge.setItem(2, new ItemStack(ModItems.INFERNAL_KEY_BIT_FRAGMENT.get()));
    }

    private static void tick(ServerLevel level, BlockPos pos, KeyForgeBlockEntity forge, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            KeyForgeBlockEntity.serverTick(level, pos, level.getBlockState(pos), forge);
        }
    }

    private static List<Item> foreignParts() {
        return List.of(
                ModItems.GHOST_KEY_BOW_FRAGMENT.get(),
                ModItems.GHOST_KEY_SHAFT_FRAGMENT.get(),
                ModItems.GHOST_KEY_BIT_FRAGMENT.get(),
                ModItems.SCULK_KEY_BOW_FRAGMENT.get(),
                ModItems.SCULK_KEY_SHAFT_FRAGMENT.get(),
                ModItems.SCULK_KEY_BIT_FRAGMENT.get(),
                ModItems.MOSSY_KEY_BOW_FRAGMENT.get(),
                ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get(),
                ModItems.MOSSY_KEY_BIT_FRAGMENT.get()
        );
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int countDrops(List<ItemEntity> drops, Item item) {
        return drops.stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private static void clearForge(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof KeyForgeBlockEntity forge) {
            forge.clearContent();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private InfernalKeyForgeGameTests() {
    }
}
