package com.geydev.kalfactions.menu;

import com.geydev.kalfactions.block.KeyForgeBlockEntity;
import com.geydev.kalfactions.block.KeyForgeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class KeyForgeMenu extends AbstractContainerMenu {
    public static final int DATA_SIZE = 2;
    public static final double MAX_DISTANCE_SQUARED = 64.0D;
    public static final int LEFT_INPUT_X = 43;
    public static final int CENTER_INPUT_X = 79;
    public static final int RIGHT_INPUT_X = 115;
    public static final int INPUT_Y = 27;
    public static final int OUTPUT_X = 79;
    public static final int OUTPUT_Y = 76;
    public static final int GHOST_LEFT_INPUT_X = 35;
    public static final int GHOST_CENTER_INPUT_X = 79;
    public static final int GHOST_RIGHT_INPUT_X = 123;
    public static final int GHOST_INPUT_Y = 29;
    public static final int GHOST_OUTPUT_X = 79;
    public static final int GHOST_OUTPUT_Y = 83;
    public static final int GHOST_PLAYER_INVENTORY_Y = 120;
    public static final int GHOST_PLAYER_HOTBAR_Y = 178;
    public static final int PLAYER_INVENTORY_X = 7;
    public static final int PLAYER_INVENTORY_Y = 108;
    public static final int PLAYER_HOTBAR_Y = 166;
    public static final int PLAYER_SLOT_START = KeyForgeBlockEntity.SLOT_COUNT;

    private final Container container;
    private final ContainerData data;
    private final BlockPos forgePos;
    private final KeyForgeType forgeType;

    public KeyForgeMenu(
            int containerId,
            Inventory playerInventory,
            BlockPos forgePos,
            KeyForgeType forgeType
    ) {
        this(
                containerId,
                playerInventory,
                new SimpleContainer(KeyForgeBlockEntity.SLOT_COUNT),
                new SimpleContainerData(DATA_SIZE),
                forgePos,
                forgeType
        );
    }

    public KeyForgeMenu(
            int containerId,
            Inventory playerInventory,
            Container container,
            ContainerData data,
            BlockPos forgePos,
            KeyForgeType forgeType
    ) {
        super(forgeType.menuType(), containerId);
        checkContainerSize(container, KeyForgeBlockEntity.SLOT_COUNT);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;
        this.forgePos = forgePos.immutable();
        this.forgeType = forgeType;
        container.startOpen(playerInventory.player);
        boolean ghost = forgeType == KeyForgeType.GHOST;
        int leftInputX = ghost ? GHOST_LEFT_INPUT_X : LEFT_INPUT_X;
        int centerInputX = ghost ? GHOST_CENTER_INPUT_X : CENTER_INPUT_X;
        int rightInputX = ghost ? GHOST_RIGHT_INPUT_X : RIGHT_INPUT_X;
        int inputY = ghost ? GHOST_INPUT_Y : INPUT_Y;
        int outputX = ghost ? GHOST_OUTPUT_X : OUTPUT_X;
        int outputY = ghost ? GHOST_OUTPUT_Y : OUTPUT_Y;
        int playerInventoryY = ghost ? GHOST_PLAYER_INVENTORY_Y : PLAYER_INVENTORY_Y;
        int playerHotbarY = ghost ? GHOST_PLAYER_HOTBAR_Y : PLAYER_HOTBAR_Y;
        addInputSlot(KeyForgeBlockEntity.BOW_SLOT, leftInputX, inputY);
        addInputSlot(KeyForgeBlockEntity.SHAFT_SLOT, centerInputX, inputY);
        addInputSlot(KeyForgeBlockEntity.BIT_SLOT, rightInputX, inputY);
        addSlot(new Slot(container, KeyForgeBlockEntity.RESULT_SLOT, outputX, outputY) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18,
                        playerInventoryY + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    playerInventory,
                    column,
                    PLAYER_INVENTORY_X + column * 18,
                    playerHotbarY
            ));
        }
        addDataSlots(data);
    }

    private void addInputSlot(int slotIndex, int x, int y) {
        addSlot(new Slot(container, slotIndex, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return inputSlotFor(stack, forgeType) == slotIndex;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
    }

    public static int inputSlotFor(ItemStack stack, KeyForgeType forgeType) {
        if (stack.is(forgeType.bowFragment())) {
            return KeyForgeBlockEntity.BOW_SLOT;
        }
        if (stack.is(forgeType.shaftFragment())) {
            return KeyForgeBlockEntity.SHAFT_SLOT;
        }
        if (stack.is(forgeType.bitFragment())) {
            return KeyForgeBlockEntity.BIT_SLOT;
        }
        return -1;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!player.level().getBlockState(forgePos).is(forgeType.block())
                || player.distanceToSqr(
                        forgePos.getX() + 0.5D,
                        forgePos.getY() + 0.5D,
                        forgePos.getZ() + 0.5D
                ) > MAX_DISTANCE_SQUARED) {
            return false;
        }
        return player.level().isClientSide()
                || container instanceof KeyForgeBlockEntity forge
                && forge.forgeType() == forgeType
                && player.level().getBlockEntity(forgePos) == forge;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (index < KeyForgeBlockEntity.SLOT_COUNT) {
            if (!moveItemStackTo(source, PLAYER_SLOT_START, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int destination = inputSlotFor(source, forgeType);
            if (destination < 0 || !moveItemStackTo(source, destination, destination + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, source);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    public int progress() {
        return Math.clamp(data.get(0), 0, totalTicks());
    }

    public int totalTicks() {
        return Math.max(1, data.get(1));
    }

    public float progressFraction() {
        return Math.clamp(progress() / (float) totalTicks(), 0.0F, 1.0F);
    }

    public int remainingTicks() {
        return Math.max(0, totalTicks() - progress());
    }

    public boolean hasCompleteInput() {
        return getSlot(KeyForgeBlockEntity.BOW_SLOT).getItem().is(forgeType.bowFragment())
                && getSlot(KeyForgeBlockEntity.SHAFT_SLOT).getItem().is(forgeType.shaftFragment())
                && getSlot(KeyForgeBlockEntity.BIT_SLOT).getItem().is(forgeType.bitFragment());
    }

    public BlockPos forgePos() {
        return forgePos;
    }

    public KeyForgeType forgeType() {
        return forgeType;
    }
}
