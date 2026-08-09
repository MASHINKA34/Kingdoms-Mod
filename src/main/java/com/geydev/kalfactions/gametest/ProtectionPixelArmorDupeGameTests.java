package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionPixelArmorDupeGameTests {
    private static final ResourceLocation HANGER = ResourceLocation.fromNamespaceAndPath("protection_pixel", "armorhanger");
    private static final ResourceLocation CHESTPLATE = ResourceLocation.fromNamespaceAndPath("protection_pixel", "tosaki_chestplate");
    private static final ResourceLocation LEGGINGS = ResourceLocation.fromNamespaceAndPath("protection_pixel", "tosaki_leggings");
    private static final BlockPos HANGER_POS = new BlockPos(1, 3, 1);
    private static final BlockPos STAND_POS = new BlockPos(1, 2, 1);
    private static final int HELD_PLATES = 4;
    private static final int WORN_PLATES = 9;

    @GameTest(template = "empty")
    public static void brokenHangerDropsOnlyItsOwnArmor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        placeHanger(helper, plated(CHESTPLATE, HELD_PLATES), plated(LEGGINGS, 3));
        player.setItemSlot(EquipmentSlot.CHEST, plated(CHESTPLATE, WORN_PLATES));
        BlockPos abs = helper.absolutePos(HANGER_POS);
        player.moveTo(abs.getX() + 0.5, abs.getY() - 2.0, abs.getZ() + 2.5);

        helper.getLevel().destroyBlock(abs, true, player);

        assertNoWornCopy(helper, droppedArmor(helper, HANGER_POS), 2, player, "Armor hanger");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedHangerDropsOnlyItsOwnArmor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        placeHanger(helper, plated(CHESTPLATE, HELD_PLATES), ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, plated(CHESTPLATE, WORN_PLATES));
        BlockPos abs = helper.absolutePos(HANGER_POS);
        player.moveTo(abs.getX() + 0.5, abs.getY() - 2.0, abs.getZ() + 2.5);

        helper.getLevel().removeBlock(abs, false);

        assertNoWornCopy(helper, droppedArmor(helper, HANGER_POS), 1, player, "Removed hanger");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void swappingArmorOnHangerConservesArmor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        placeHanger(helper, plated(CHESTPLATE, HELD_PLATES), ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, plated(CHESTPLATE, WORN_PLATES));
        player.setItemInHand(InteractionHand.MAIN_HAND, plated(CHESTPLATE, 7));
        BlockPos abs = helper.absolutePos(HANGER_POS);
        player.moveTo(abs.getX() + 0.5, abs.getY() - 2.0, abs.getZ() + 1.5);

        helper.useBlock(HANGER_POS, player);

        int inInventory = countChestplates(player);
        int onHanger = ((Container) helper.getLevel().getBlockEntity(abs)).getItem(0).isEmpty() ? 0 : 1;
        helper.assertTrue(
                inInventory + onHanger == 3,
                "Hanger swap turned 3 chestplates into " + (inInventory + onHanger)
                        + " (inventory=" + inInventory + ", hanger=" + onHanger + ")"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorStandBrokenByPlayerDropsOnlyItsOwnArmor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ArmorStand stand = placeStand(helper, plated(CHESTPLATE, HELD_PLATES), plated(LEGGINGS, 3));
        player.setItemSlot(EquipmentSlot.CHEST, plated(CHESTPLATE, WORN_PLATES));
        BlockPos abs = helper.absolutePos(STAND_POS);
        player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5);

        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        stand.hurt(source, 1.0F);
        stand.hurt(source, 1.0F);

        helper.assertTrue(stand.isRemoved(), "Armor stand survived two consecutive player hits");
        assertNoWornCopy(helper, droppedArmor(helper, STAND_POS), 2, player, "Armor stand");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorStandBrokenByExplosionDropsOnlyItsOwnArmor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ArmorStand stand = placeStand(helper, plated(CHESTPLATE, HELD_PLATES), plated(LEGGINGS, 3));
        player.setItemSlot(EquipmentSlot.CHEST, plated(CHESTPLATE, WORN_PLATES));
        BlockPos abs = helper.absolutePos(STAND_POS);
        player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 6.5);

        stand.hurt(helper.getLevel().damageSources().explosion(null, null), 20.0F);

        helper.assertTrue(stand.isRemoved(), "Armor stand survived an explosion");
        assertNoWornCopy(helper, droppedArmor(helper, STAND_POS), 2, player, "Exploded armor stand");
        helper.succeed();
    }

    private static void assertNoWornCopy(
            GameTestHelper helper, List<ItemStack> dropped, int expected, Player player, String what
    ) {
        helper.assertFalse(
                dropped.stream().anyMatch(stack -> plateValue(stack) == WORN_PLATES),
                what + " dropped a copy of the worn chestplate: " + describe(dropped)
        );
        helper.assertTrue(
                plateValue(player.getItemBySlot(EquipmentSlot.CHEST)) == WORN_PLATES,
                what + " altered the worn chestplate"
        );
        helper.assertTrue(
                dropped.size() == expected,
                what + " dropped " + dropped.size() + " armor pieces instead of " + expected + ": " + describe(dropped)
        );
    }

    private static void placeHanger(GameTestHelper helper, ItemStack chest, ItemStack legs) {
        Block hanger = BuiltInRegistries.BLOCK.get(HANGER);
        helper.setBlock(HANGER_POS, hanger.defaultBlockState());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(HANGER_POS));
        helper.assertTrue(blockEntity instanceof Container, "Armor hanger has no container block entity");
        Container container = (Container) blockEntity;
        container.setItem(0, chest.copy());
        if (!legs.isEmpty()) {
            container.setItem(1, legs.copy());
        }
        blockEntity.setChanged();
    }

    private static ArmorStand placeStand(GameTestHelper helper, ItemStack chest, ItemStack legs) {
        BlockPos abs = helper.absolutePos(STAND_POS);
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        helper.assertTrue(stand != null, "Could not create an armor stand");
        stand.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0F, 0.0F);
        stand.setItemSlot(EquipmentSlot.CHEST, chest.copy());
        stand.setItemSlot(EquipmentSlot.LEGS, legs.copy());
        helper.getLevel().addFreshEntity(stand);
        return stand;
    }

    private static List<ItemStack> droppedArmor(GameTestHelper helper, BlockPos around) {
        BlockPos abs = helper.absolutePos(around);
        return helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3.0))
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.getItem() instanceof ArmorItem)
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("protection_pixel"))
                .toList();
    }

    private static int countChestplates(Player player) {
        Item chestplate = BuiltInRegistries.ITEM.get(CHESTPLATE);
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == chestplate) {
                found += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (stack.getItem() == chestplate) {
                found += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() == chestplate) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static ItemStack plated(ResourceLocation id, int plateArmor) {
        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putDouble("platearmor", plateArmor);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static int plateValue(ItemStack stack) {
        return (int) stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("platearmor");
    }

    private static String describe(List<ItemStack> stacks) {
        return stacks.stream()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount()
                        + "{platearmor=" + plateValue(stack) + "}")
                .toList()
                .toString();
    }
}
