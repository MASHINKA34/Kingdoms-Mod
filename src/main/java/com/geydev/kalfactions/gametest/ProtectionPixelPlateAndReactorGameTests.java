package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import io.netty.buffer.Unpooled;
import net.mcreator.protectionpixel.network.LegguiButtonMessage;
import net.mcreator.protectionpixel.network.ProtectionPixelModVariables;
import net.mcreator.protectionpixel.network.ZongButtonMessage;
import net.mcreator.protectionpixel.procedures.ZongduringProcedure;
import net.mcreator.protectionpixel.world.inventory.LegguiMenu;
import net.mcreator.protectionpixel.world.inventory.ZongMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionPixelPlateAndReactorGameTests {
    private static final ResourceLocation PLATFORM = pp("armorloadplatform");
    private static final ResourceLocation LEGGINGS = pp("tosaki_leggings");
    private static final ResourceLocation PLATE = pp("ironarmorplate");
    private static final ResourceLocation REACTOR = pp("powerengine");
    private static final BlockPos PLATFORM_POS = new BlockPos(1, 2, 1);

    @GameTest(template = "empty")
    public static void connectedLegPlatesAreConsumedNotHandedBack(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);
        BlockPos abs = abs(helper);
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(BuiltInRegistries.ITEM.get(LEGGINGS)));

        AbstractContainerMenu zong = open(player, new ZongMenu(1, player.getInventory(), buf(helper)));
        ZongButtonMessage.handleButtonAction(player, 2, abs.getX(), abs.getY(), abs.getZ());
        ZongButtonMessage.handleButtonAction(player, 4, abs.getX(), abs.getY(), abs.getZ());
        close(player, zong);
        AbstractContainerMenu leg = open(player, new LegguiMenu(2, player.getInventory(), buf(helper)));
        platform.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(LEGGINGS)));
        platform.setItem(1, plate());
        platform.setItem(2, plate());

        LegguiButtonMessage.handleButtonAction(player, 0, abs.getX(), abs.getY(), abs.getZ());
        close(player, leg);

        helper.assertTrue(
                countPlates(player) == 0,
                "Connected plates were handed back to the player: " + countPlates(player) + " plate(s) duplicated"
        );
        helper.assertTrue(
                plateArmor(player.getItemBySlot(EquipmentSlot.LEGS)) == 4.0,
                "Connecting applied " + plateArmor(player.getItemBySlot(EquipmentSlot.LEGS))
                        + " plate armor to the worn leggings instead of 4.0"
        );
        for (int slot = 0; slot < platform.getContainerSize(); slot++) {
            helper.assertTrue(
                    platform.getItem(slot).isEmpty(),
                    "Platform slot " + slot + " still holds " + platform.getItem(slot)
            );
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reactorInThePowerSlotSurvivesClosingTheMenu(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);

        AbstractContainerMenu zong = open(player, new ZongMenu(1, player.getInventory(), buf(helper)));
        platform.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(REACTOR)));
        ZongduringProcedure.execute(player);
        close(player, zong);

        ItemStack stored = player.getData(ProtectionPixelModVariables.PLAYER_VARIABLES).powerslot;
        helper.assertTrue(
                stored.getItem() == BuiltInRegistries.ITEM.get(REACTOR),
                "The reactor was not stored in the player's power slot, found: " + stored
        );
        helper.assertTrue(platform.getItem(1).isEmpty(), "The reactor was left inside the platform as well");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reactorIsNotLostWhenTheMenuClosesImmediately(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);

        AbstractContainerMenu zong = open(player, new ZongMenu(1, player.getInventory(), buf(helper)));
        platform.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(REACTOR)));
        close(player, zong);

        ItemStack stored = player.getData(ProtectionPixelModVariables.PLAYER_VARIABLES).powerslot;
        boolean keptInVariables = stored.getItem() == BuiltInRegistries.ITEM.get(REACTOR);
        boolean keptInPlatform = platform.getItem(1).getItem() == BuiltInRegistries.ITEM.get(REACTOR);
        helper.assertTrue(
                keptInVariables || keptInPlatform,
                "Closing the menu in the same tick destroyed the reactor"
        );
        helper.succeed();
    }

    private static Player setUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(PLATFORM_POS, BuiltInRegistries.BLOCK.get(PLATFORM).defaultBlockState());
        IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs(helper), null);
        helper.assertTrue(tank != null, "Armor Load Platform has no fluid tank");
        tank.fill(new FluidStack(Fluids.LAVA, 200), IFluidHandler.FluidAction.EXECUTE);
        BlockPos abs = abs(helper);
        player.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
        return player;
    }

    private static AbstractContainerMenu open(Player player, AbstractContainerMenu menu) {
        player.containerMenu = menu;
        return menu;
    }

    private static void close(Player player, AbstractContainerMenu menu) {
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
    }

    private static ItemStack plate() {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(PLATE));
        CompoundTag tag = new CompoundTag();
        tag.putDouble("armor", 2.0);
        tag.putDouble("toughness", 1.0);
        tag.putDouble("weight", 1.5);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static int countPlates(Player player) {
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == BuiltInRegistries.ITEM.get(PLATE)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    private static double plateArmor(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("platearmor");
    }

    private static Container platform(GameTestHelper helper) {
        return (Container) helper.getLevel().getBlockEntity(abs(helper));
    }

    private static BlockPos abs(GameTestHelper helper) {
        return helper.absolutePos(PLATFORM_POS);
    }

    private static FriendlyByteBuf buf(GameTestHelper helper) {
        return new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(abs(helper));
    }

    private static ResourceLocation pp(String path) {
        return ResourceLocation.fromNamespaceAndPath("protection_pixel", path);
    }
}
