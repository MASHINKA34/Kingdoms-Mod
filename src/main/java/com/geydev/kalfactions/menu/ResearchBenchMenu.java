package com.geydev.kalfactions.menu;

import com.geydev.kalfactions.registry.ModMenuTypes;
import com.geydev.kalfactions.science.ResearchBenchStatus;
import com.geydev.kalfactions.science.ScienceInputs;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ResearchBenchMenu extends AbstractContainerMenu {
    public static final int SLOTS = 9;
    public static final int DATA_SIZE = 6;
    public static final int INPUT_COLUMNS = 3;
    public static final int INPUT_SLOT_X = 26;
    public static final int INPUT_SLOT_Y = 40;
    public static final int INPUT_SLOT_STEP = 20;
    public static final int PLAYER_INVENTORY_X = 32;
    public static final int PLAYER_INVENTORY_Y = 122;
    public static final int PLAYER_HOTBAR_Y = 180;

    private final Container container;
    private final ContainerData data;

    public ResearchBenchMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(SLOTS), new SimpleContainerData(DATA_SIZE));
    }

    public ResearchBenchMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenuTypes.RESEARCH_BENCH.get(), containerId);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;
        container.startOpen(playerInventory.player);
        for (int slot = 0; slot < SLOTS; slot++) {
            int row = slot / INPUT_COLUMNS;
            int column = slot % INPUT_COLUMNS;
            addSlot(new Slot(
                    container,
                    slot,
                    INPUT_SLOT_X + column * INPUT_SLOT_STEP,
                    INPUT_SLOT_Y + row * INPUT_SLOT_STEP
            ) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ScienceInputs.all().isEmpty() || ScienceInputs.accepts(stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18,
                        PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * 18, PLAYER_HOTBAR_Y));
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

    public ResearchBenchStatus status() {
        return ResearchBenchStatus.byOrdinal(data.get(2));
    }

    public int scienceToday() {
        return Math.max(0, data.get(3));
    }

    public int dailyCap() {
        return Math.max(0, data.get(4));
    }

    public boolean capped() {
        return dailyCap() > 0;
    }

    public int currentScience() {
        return Math.max(0, data.get(5));
    }
}
