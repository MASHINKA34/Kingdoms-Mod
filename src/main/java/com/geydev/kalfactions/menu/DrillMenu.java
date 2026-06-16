package com.geydev.kalfactions.menu;

import com.geydev.kalfactions.registry.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class DrillMenu extends AbstractContainerMenu {
    private static final int SLOTS = 18;
    private final Container container;
    private final ContainerData data;

    public DrillMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(SLOTS), new SimpleContainerData(2));
    }

    public DrillMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenuTypes.DRILL.get(), containerId);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(container, column + row * 9, 18 + column * 18, 52 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 48 + column * 18, 106 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 48 + column * 18, 166));
        }
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack source = slot.getItem();
            result = source.copy();
            if (index < SLOTS) {
                if (!moveItemStackTo(source, SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(source, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (source.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public int progress() {
        return Math.max(0, data.get(0));
    }

    public int intervalTicks() {
        return Math.max(1, data.get(1));
    }

    public float progressFraction() {
        return Math.clamp(progress() / (float) intervalTicks(), 0.0F, 1.0F);
    }

    public int remainingTicks() {
        return Math.max(0, intervalTicks() - progress());
    }
}
