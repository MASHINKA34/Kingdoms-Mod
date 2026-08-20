package com.geydev.kalfactions.block;

import com.geydev.kalfactions.menu.KeyForgeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class KeyForgeBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int ASSEMBLY_TICKS = 100;
    public static final int BOW_SLOT = 0;
    public static final int SHAFT_SLOT = 1;
    public static final int BIT_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    public static final int SLOT_COUNT = 4;
    private static final String TAG_PROGRESS = "AssemblyProgress";

    private final KeyForgeType forgeType;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> ASSEMBLY_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = Math.clamp(value, 0, ASSEMBLY_TICKS);
            }
        }

        @Override
        public int getCount() {
            return KeyForgeMenu.DATA_SIZE;
        }
    };
    private int progress;

    public KeyForgeBlockEntity(KeyForgeType forgeType, BlockPos pos, BlockState state) {
        super(forgeType.blockEntityType(), pos, state);
        this.forgeType = forgeType;
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            KeyForgeBlockEntity forge
    ) {
        if (level.isClientSide()) {
            return;
        }
        forge.tickAssembly();
    }

    private void tickAssembly() {
        if (!hasCompleteInput()) {
            resetProgress();
            return;
        }
        if (!items.get(RESULT_SLOT).isEmpty()) {
            return;
        }
        progress++;
        if (progress >= ASSEMBLY_TICKS) {
            items.get(BOW_SLOT).shrink(1);
            items.get(SHAFT_SLOT).shrink(1);
            items.get(BIT_SLOT).shrink(1);
            items.set(RESULT_SLOT, new ItemStack(forgeType.result()));
            progress = 0;
            if (forgeType == KeyForgeType.INFERNAL && level != null) {
                level.playSound(
                        null,
                        worldPosition,
                        SoundEvents.SMITHING_TABLE_USE,
                        SoundSource.BLOCKS,
                        1.0F,
                        0.9F
                );
            }
        }
        setChanged();
    }

    private boolean hasCompleteInput() {
        return items.get(BOW_SLOT).is(forgeType.bowFragment())
                && items.get(SHAFT_SLOT).is(forgeType.shaftFragment())
                && items.get(BIT_SLOT).is(forgeType.bitFragment());
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    public KeyForgeType forgeType() {
        return forgeType;
    }

    public int progressTicks() {
        return progress;
    }

    public void dropContents(ServerLevel level, BlockPos pos) {
        Containers.dropContents(level, pos, this);
        clearContent();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            if (slot != RESULT_SLOT) {
                progress = 0;
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) {
            if (slot != RESULT_SLOT) {
                progress = 0;
            }
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= RESULT_SLOT || (!stack.isEmpty() && !canPlaceItem(slot, stack))) {
            return;
        }
        ItemStack stored = stack.copyWithCount(Math.min(1, stack.getCount()));
        if (!ItemStack.matches(items.get(slot), stored)) {
            items.set(slot, stored);
            progress = 0;
            setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case BOW_SLOT -> stack.is(forgeType.bowFragment());
            case SHAFT_SLOT -> stack.is(forgeType.shaftFragment());
            case BIT_SLOT -> stack.is(forgeType.bitFragment());
            default -> false;
        };
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockState(worldPosition).is(forgeType.block())
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D
                ) <= KeyForgeMenu.MAX_DISTANCE_SQUARED;
    }

    @Override
    public void clearContent() {
        items.clear();
        progress = 0;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(forgeType.displayNameKey());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new KeyForgeMenu(containerId, playerInventory, this, dataAccess, worldPosition, forgeType);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        progress = Math.clamp(tag.getInt(TAG_PROGRESS), 0, ASSEMBLY_TICKS - 1);
        sanitizeInventory();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt(TAG_PROGRESS, progress);
    }

    private void sanitizeInventory() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if ((slot == RESULT_SLOT && !stack.is(forgeType.result()))
                    || (slot != RESULT_SLOT && !canPlaceItem(slot, stack))) {
                items.set(slot, ItemStack.EMPTY);
                progress = 0;
            } else {
                stack.setCount(1);
            }
        }
    }
}
