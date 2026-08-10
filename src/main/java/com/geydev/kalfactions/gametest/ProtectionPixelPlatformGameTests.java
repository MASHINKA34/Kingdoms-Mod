package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.mcreator.protectionpixel.network.HeadguiButtonMessage;
import net.mcreator.protectionpixel.network.ZongButtonMessage;
import net.mcreator.protectionpixel.world.inventory.HeadguiMenu;
import net.mcreator.protectionpixel.world.inventory.ZongMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProtectionPixelPlatformGameTests {
    private static final ResourceLocation PLATFORM = pp("armorloadplatform");
    private static final ResourceLocation HELMET = pp("plague_helmet");
    private static final ResourceLocation PLATE = pp("ironarmorplate");
    private static final BlockPos PLATFORM_POS = new BlockPos(1, 2, 1);

    @GameTest(template = "empty")
    public static void selectingArmorThenLeavingLeavesNoCopyBehind(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);

        AbstractContainerMenu zong = open(helper, player, new ZongMenu(1, player.getInventory(), buf(helper)));
        ZongButtonMessage.handleButtonAction(player, 0, abs(helper).getX(), abs(helper).getY(), abs(helper).getZ());
        helper.assertFalse(platform.getItem(0).isEmpty(), "Selecting the helmet did not fill the platform display slot");

        close(player, zong);

        helper.assertTrue(
                platform.getItem(0).isEmpty(),
                "Leaving the platform menu left a copy of the worn helmet in slot 0: " + describe(platform)
        );
        assertNoArmorDropped(helper, player);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void openingThePartMenuThenLeavingLeavesNoCopyBehind(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);
        BlockPos abs = abs(helper);

        AbstractContainerMenu zong = open(helper, player, new ZongMenu(1, player.getInventory(), buf(helper)));
        ZongButtonMessage.handleButtonAction(player, 0, abs.getX(), abs.getY(), abs.getZ());
        ZongButtonMessage.handleButtonAction(player, 4, abs.getX(), abs.getY(), abs.getZ());
        close(player, zong);
        AbstractContainerMenu head = open(helper, player, new HeadguiMenu(2, player.getInventory(), buf(helper)));
        platform.setItem(0, plated(HELMET));

        close(player, head);

        helper.assertTrue(
                platform.getItem(0).isEmpty(),
                "Leaving the helmet menu left a copy of the worn helmet in slot 0: " + describe(platform)
        );
        assertNoArmorDropped(helper, player);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void connectingPlatesThenLeavingLeavesNoCopyBehind(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);
        BlockPos abs = abs(helper);

        AbstractContainerMenu zong = open(helper, player, new ZongMenu(1, player.getInventory(), buf(helper)));
        ZongButtonMessage.handleButtonAction(player, 0, abs.getX(), abs.getY(), abs.getZ());
        ZongButtonMessage.handleButtonAction(player, 4, abs.getX(), abs.getY(), abs.getZ());
        close(player, zong);
        AbstractContainerMenu head = open(helper, player, new HeadguiMenu(2, player.getInventory(), buf(helper)));
        platform.setItem(0, plated(HELMET));
        platform.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(PLATE)));
        platform.setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(PLATE)));

        HeadguiButtonMessage.handleButtonAction(player, 0, abs.getX(), abs.getY(), abs.getZ());
        close(player, head);

        helper.assertTrue(
                platform.getItem(0).isEmpty(),
                "Connecting plates left a copy of the worn helmet in slot 0: " + describe(platform)
        );
        assertNoArmorDropped(helper, player);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void platformItemHandlerCapabilityIsUsable(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);
        platform.setItem(0, plated(HELMET));
        Object handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs(helper), null);
        helper.assertTrue(handler != null, "ItemHandler.BLOCK capability is NULL for side=null");
        helper.assertTrue(
                handler instanceof net.neoforged.neoforge.items.IItemHandlerModifiable,
                "capability is not IItemHandlerModifiable: " + handler.getClass().getName()
        );
        net.neoforged.neoforge.items.IItemHandlerModifiable modifiable =
                (net.neoforged.neoforge.items.IItemHandlerModifiable) handler;
        helper.assertTrue(modifiable.getSlots() == 6, "capability exposes " + modifiable.getSlots() + " slots, expected 6");
        modifiable.setStackInSlot(0, ItemStack.EMPTY);
        helper.assertTrue(platform.getItem(0).isEmpty(), "setStackInSlot(0, EMPTY) did not clear the container slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void platformBrokenAfterAFullVisitDropsNoWornArmor(GameTestHelper helper) {
        Player player = setUp(helper);
        Container platform = platform(helper);
        BlockPos abs = abs(helper);

        AbstractContainerMenu zong = open(helper, player, new ZongMenu(1, player.getInventory(), buf(helper)));
        ZongButtonMessage.handleButtonAction(player, 0, abs.getX(), abs.getY(), abs.getZ());
        ZongButtonMessage.handleButtonAction(player, 4, abs.getX(), abs.getY(), abs.getZ());
        close(player, zong);
        AbstractContainerMenu head = open(helper, player, new HeadguiMenu(2, player.getInventory(), buf(helper)));
        platform.setItem(0, plated(HELMET));
        platform.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(PLATE)));
        platform.setItem(2, new ItemStack(BuiltInRegistries.ITEM.get(PLATE)));
        HeadguiButtonMessage.handleButtonAction(player, 0, abs.getX(), abs.getY(), abs.getZ());
        close(player, head);

        helper.getLevel().destroyBlock(abs, true, player);

        assertNoArmorDropped(helper, player);
        helper.succeed();
    }

    private static Player setUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(PLATFORM_POS, BuiltInRegistries.BLOCK.get(PLATFORM).defaultBlockState());
        IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, abs(helper), null);
        helper.assertTrue(tank != null, "Armor Load Platform has no fluid tank");
        tank.fill(new FluidStack(Fluids.LAVA, 200), IFluidHandler.FluidAction.EXECUTE);
        player.setItemSlot(EquipmentSlot.HEAD, plated(HELMET));
        BlockPos abs = abs(helper);
        player.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
        return player;
    }

    private static AbstractContainerMenu open(GameTestHelper helper, Player player, AbstractContainerMenu menu) {
        player.containerMenu = menu;
        return menu;
    }

    private static void close(Player player, AbstractContainerMenu menu) {
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
    }

    private static void assertNoArmorDropped(GameTestHelper helper, Player player) {
        List<ItemStack> dropped = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(abs(helper)).inflate(3.0))
                .stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.getItem() instanceof ArmorItem)
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("protection_pixel"))
                .toList();
        helper.assertTrue(
                dropped.isEmpty(),
                "Platform dropped armor that is still worn: " + dropped.stream()
                        .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        .toList()
        );
        helper.assertFalse(
                player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                "The worn helmet disappeared from the player"
        );
    }

    private static Container platform(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(abs(helper));
        return (Container) blockEntity;
    }

    private static String describe(Container container) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            builder.append(i).append('=').append(stack.isEmpty() ? "-" : BuiltInRegistries.ITEM.getKey(stack.getItem())).append(' ');
        }
        return builder.toString();
    }

    private static ItemStack plated(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
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
