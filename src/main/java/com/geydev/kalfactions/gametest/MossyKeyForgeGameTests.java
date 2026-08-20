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
public final class MossyKeyForgeGameTests {
    @GameTest(template = "empty", batch = "mossy_key_forge", timeoutTicks = 200)
    public static void assemblyPersistsContinuesClosedAndConsumesExactlyOneSet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        fillRecipe(forge);
        tick(level, pos, forge, 33);
        KeyForgeMenu menu = (KeyForgeMenu) forge.createMenu(1, player.getInventory(), player);
        helper.assertValueEqual(menu.progress(), 33, "open menu progress");
        menu.removed(player);
        tick(level, pos, forge, 17);

        CompoundTag saved = forge.saveWithoutMetadata(level.registryAccess());
        forge.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        forge = placeForge(level, pos);
        forge.loadCustomOnly(saved, level.registryAccess());

        helper.assertValueEqual(forge.progressTicks(), 50, "saved progress");
        helper.assertTrue(forge.getItem(0).is(ModItems.MOSSY_KEY_BOW_FRAGMENT.get()), "saved bow");
        helper.assertTrue(forge.getItem(1).is(ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get()), "saved shaft");
        helper.assertTrue(forge.getItem(2).is(ModItems.MOSSY_KEY_BIT_FRAGMENT.get()), "saved bit");
        tick(level, pos, forge, 50);
        helper.assertTrue(forge.getItem(0).isEmpty(), "bow consumed");
        helper.assertTrue(forge.getItem(1).isEmpty(), "shaft consumed");
        helper.assertTrue(forge.getItem(2).isEmpty(), "bit consumed");
        helper.assertTrue(forge.getItem(3).is(ModItems.MOSSY_KEY.get()), "assembled key");
        helper.assertValueEqual(forge.getItem(3).getCount(), 1, "assembled key count");
        helper.assertValueEqual(forge.progressTicks(), 0, "completed progress");
        clearForge(level, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mossy_key_forge", timeoutTicks = 200)
    public static void slotsRejectWrongPartsKeysAndForeignItemsThenResetAndPause(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);

        for (int slot = 0; slot < KeyForgeBlockEntity.RESULT_SLOT; slot++) {
            Item expected = expectedForSlot(slot);
            helper.assertTrue(forge.canPlaceItem(slot, new ItemStack(expected)), "expected fragment rejected");
            helper.assertTrue(!forge.canPlaceItem(slot, new ItemStack(Items.STONE)), "stone accepted");
            for (Item fragment : allFragments()) {
                if (fragment != expected) {
                    helper.assertTrue(
                            !forge.canPlaceItem(slot, new ItemStack(fragment)),
                            "wrong fragment accepted in slot " + slot
                    );
                }
            }
            for (Item key : allKeys()) {
                helper.assertTrue(!forge.canPlaceItem(slot, new ItemStack(key)), "assembled key accepted");
            }
        }
        helper.assertTrue(
                !forge.canPlaceItem(KeyForgeBlockEntity.RESULT_SLOT, new ItemStack(ModItems.MOSSY_KEY.get())),
                "result slot accepts insertion"
        );
        forge.setItem(KeyForgeBlockEntity.RESULT_SLOT, new ItemStack(ModItems.MOSSY_KEY.get()));
        helper.assertTrue(forge.getItem(KeyForgeBlockEntity.RESULT_SLOT).isEmpty(), "result insertion succeeded");

        fillRecipe(forge);
        tick(level, pos, forge, 25);
        forge.removeItem(KeyForgeBlockEntity.SHAFT_SLOT, 1);
        helper.assertValueEqual(forge.progressTicks(), 0, "progress after fragment removal");
        forge.setItem(KeyForgeBlockEntity.SHAFT_SLOT, new ItemStack(ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get()));
        tick(level, pos, forge, KeyForgeBlockEntity.ASSEMBLY_TICKS);
        helper.assertTrue(forge.getItem(KeyForgeBlockEntity.RESULT_SLOT).is(ModItems.MOSSY_KEY.get()), "result missing");

        fillRecipe(forge);
        tick(level, pos, forge, 40);
        helper.assertValueEqual(forge.progressTicks(), 0, "occupied output advanced progress");
        helper.assertValueEqual(forge.getItem(0).getCount(), 1, "paused bow consumed");
        helper.assertValueEqual(forge.getItem(1).getCount(), 1, "paused shaft consumed");
        helper.assertValueEqual(forge.getItem(2).getCount(), 1, "paused bit consumed");
        forge.removeItem(KeyForgeBlockEntity.RESULT_SLOT, 1);
        tick(level, pos, forge, 1);
        helper.assertValueEqual(forge.progressTicks(), 1, "assembly did not resume");
        clearForge(level, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mossy_key_forge", timeoutTicks = 200)
    public static void shiftClickRoutesOnlyMossyPartsAndCannotDuplicateResult(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        player.getInventory().setItem(9, new ItemStack(ModItems.MOSSY_KEY_BOW_FRAGMENT.get(), 2));
        player.getInventory().setItem(10, new ItemStack(ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get()));
        player.getInventory().setItem(11, new ItemStack(ModItems.MOSSY_KEY_BIT_FRAGMENT.get()));
        player.getInventory().setItem(12, new ItemStack(ModItems.GHOST_KEY_BOW_FRAGMENT.get()));
        player.getInventory().setItem(13, new ItemStack(ModItems.MOSSY_KEY.get()));

        KeyForgeMenu menu = (KeyForgeMenu) forge.createMenu(1, player.getInventory(), player);
        helper.assertTrue(
                !menu.getSlot(KeyForgeBlockEntity.BOW_SLOT)
                        .mayPlace(new ItemStack(ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get())),
                "shaft accepted by bow slot"
        );
        helper.assertTrue(
                !menu.getSlot(KeyForgeBlockEntity.SHAFT_SLOT)
                        .mayPlace(new ItemStack(ModItems.MOSSY_KEY_BIT_FRAGMENT.get())),
                "bit accepted by shaft slot"
        );
        helper.assertTrue(
                !menu.getSlot(KeyForgeBlockEntity.BIT_SLOT)
                        .mayPlace(new ItemStack(ModItems.MOSSY_KEY_BOW_FRAGMENT.get())),
                "bow accepted by bit slot"
        );
        helper.assertTrue(
                !menu.getSlot(KeyForgeBlockEntity.RESULT_SLOT).mayPlace(new ItemStack(ModItems.MOSSY_KEY.get())),
                "result slot accepts placement"
        );
        helper.assertTrue(!menu.quickMoveStack(player, 4).isEmpty(), "bow shift-click failed");
        helper.assertTrue(!menu.quickMoveStack(player, 5).isEmpty(), "shaft shift-click failed");
        helper.assertTrue(!menu.quickMoveStack(player, 6).isEmpty(), "bit shift-click failed");
        helper.assertTrue(menu.quickMoveStack(player, 7).isEmpty(), "foreign fragment shift-click succeeded");
        helper.assertTrue(menu.quickMoveStack(player, 8).isEmpty(), "assembled key shift-click succeeded");
        helper.assertValueEqual(forge.getItem(0).getCount(), 1, "bow input count");
        helper.assertValueEqual(player.getInventory().getItem(9).getCount(), 1, "bow remainder");
        helper.assertTrue(forge.getItem(1).is(ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get()), "shaft routing");
        helper.assertTrue(forge.getItem(2).is(ModItems.MOSSY_KEY_BIT_FRAGMENT.get()), "bit routing");

        tick(level, pos, forge, KeyForgeBlockEntity.ASSEMBLY_TICKS);
        helper.assertTrue(!menu.quickMoveStack(player, KeyForgeBlockEntity.RESULT_SLOT).isEmpty(), "result shift-click failed");
        helper.assertValueEqual(count(player, ModItems.MOSSY_KEY.get()), 2, "key count after result extraction");
        helper.assertTrue(
                menu.quickMoveStack(player, KeyForgeBlockEntity.RESULT_SLOT).isEmpty(),
                "empty result duplicated key"
        );
        helper.assertValueEqual(count(player, ModItems.MOSSY_KEY.get()), 2, "key count after duplicate attempt");
        menu.removed(player);
        clearForge(level, pos);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "mossy_key_forge", timeoutTicks = 200)
    public static void breakingForgeDropsEveryStoredFragmentOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        KeyForgeBlockEntity forge = placeForge(level, pos);
        fillRecipe(forge);
        tick(level, pos, forge, 20);
        level.destroyBlock(pos, false);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D));
        helper.assertValueEqual(countDrops(drops, ModItems.MOSSY_KEY_BOW_FRAGMENT.get()), 1, "dropped bows");
        helper.assertValueEqual(countDrops(drops, ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get()), 1, "dropped shafts");
        helper.assertValueEqual(countDrops(drops, ModItems.MOSSY_KEY_BIT_FRAGMENT.get()), 1, "dropped bits");
        helper.assertValueEqual(countDrops(drops, ModItems.MOSSY_KEY.get()), 0, "unexpected key drop");
        drops.forEach(Entity::discard);
        helper.succeed();
    }

    private static KeyForgeBlockEntity placeForge(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, ModBlocks.MOSSY_KEY_FORGE.get().defaultBlockState(), 3);
        return (KeyForgeBlockEntity) level.getBlockEntity(pos);
    }

    private static void fillRecipe(KeyForgeBlockEntity forge) {
        forge.setItem(0, new ItemStack(ModItems.MOSSY_KEY_BOW_FRAGMENT.get()));
        forge.setItem(1, new ItemStack(ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get()));
        forge.setItem(2, new ItemStack(ModItems.MOSSY_KEY_BIT_FRAGMENT.get()));
    }

    private static void tick(ServerLevel level, BlockPos pos, KeyForgeBlockEntity forge, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            KeyForgeBlockEntity.serverTick(level, pos, level.getBlockState(pos), forge);
        }
    }

    private static Item expectedForSlot(int slot) {
        return switch (slot) {
            case KeyForgeBlockEntity.BOW_SLOT -> ModItems.MOSSY_KEY_BOW_FRAGMENT.get();
            case KeyForgeBlockEntity.SHAFT_SLOT -> ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get();
            case KeyForgeBlockEntity.BIT_SLOT -> ModItems.MOSSY_KEY_BIT_FRAGMENT.get();
            default -> throw new IllegalArgumentException("Not an input slot: " + slot);
        };
    }

    private static List<Item> allFragments() {
        return List.of(
                ModItems.GHOST_KEY_BOW_FRAGMENT.get(),
                ModItems.GHOST_KEY_SHAFT_FRAGMENT.get(),
                ModItems.GHOST_KEY_BIT_FRAGMENT.get(),
                ModItems.SCULK_KEY_BOW_FRAGMENT.get(),
                ModItems.SCULK_KEY_SHAFT_FRAGMENT.get(),
                ModItems.SCULK_KEY_BIT_FRAGMENT.get(),
                ModItems.INFERNAL_KEY_BOW_FRAGMENT.get(),
                ModItems.INFERNAL_KEY_SHAFT_FRAGMENT.get(),
                ModItems.INFERNAL_KEY_BIT_FRAGMENT.get(),
                ModItems.MOSSY_KEY_BOW_FRAGMENT.get(),
                ModItems.MOSSY_KEY_SHAFT_FRAGMENT.get(),
                ModItems.MOSSY_KEY_BIT_FRAGMENT.get()
        );
    }

    private static List<Item> allKeys() {
        return List.of(
                ModItems.GHOST_KEY.get(),
                ModItems.SCULK_KEY.get(),
                ModItems.INFERNAL_KEY.get(),
                ModItems.MOSSY_KEY.get()
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

    private MossyKeyForgeGameTests() {
    }
}
