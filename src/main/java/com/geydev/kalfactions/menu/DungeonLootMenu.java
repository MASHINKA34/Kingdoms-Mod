package com.geydev.kalfactions.menu;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class DungeonLootMenu extends AbstractContainerMenu {
    public static final int PLAN_SIZE = DungeonChestBlockEntity.SIZE;
    public static final int GRID_LEFT = 12;
    public static final int GRID_TOP = 30;
    public static final int INVENTORY_LEFT = 12;
    public static final int INVENTORY_TOP = 162;
    private static final double MAX_DISTANCE_SQUARED = 64.0D;

    private final BlockPos pos;
    private final Container plan;

    public DungeonLootMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, planContainer(inventory, pos));
    }

    private DungeonLootMenu(int containerId, Inventory inventory, BlockPos pos, Container plan) {
        super(ModMenuTypes.DUNGEON_LOOT.get(), containerId);
        this.pos = pos.immutable();
        this.plan = plan;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        plan,
                        row * 9 + column,
                        GRID_LEFT + column * 18 + 1,
                        GRID_TOP + row * 18 + 1
                ));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        INVENTORY_LEFT + column * 18 + 1,
                        INVENTORY_TOP + row * 18 + 1
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_LEFT + column * 18 + 1, INVENTORY_TOP + 59));
        }
    }

    private static Container planContainer(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof DungeonChestBlockEntity chest
                ? chest.planContainer()
                : new SimpleContainer(PLAN_SIZE);
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack planItem(int slot) {
        return slot >= 0 && slot < PLAN_SIZE ? plan.getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < PLAN_SIZE && clickType != ClickType.QUICK_MOVE) {
            ItemStack carried = getCarried();
            plan.setItem(slotId, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < PLAN_SIZE) {
            plan.setItem(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = source.getItem();
        for (int slot = 0; slot < PLAN_SIZE; slot++) {
            if (plan.getItem(slot).isEmpty()) {
                plan.setItem(slot, stack.copyWithCount(1));
                break;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        return player.hasPermissions(2)
                && level.getBlockState(pos).is(ModBlocks.DUNGEON_CHEST.get())
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                        <= MAX_DISTANCE_SQUARED;
    }
}
