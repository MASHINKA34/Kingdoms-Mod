package com.geydev.kalfactions.block;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dungeon.DungeonClock;
import com.geydev.kalfactions.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonChestBlockEntity extends BaseContainerBlockEntity implements LidBlockEntity {
    public static final int SIZE = 27;
    public static final int MAX_COOLDOWN_HOURS = 8760;
    public static final int MAX_COUNT = 64;
    public static final int DEFAULT_CHANCE = 100;

    private static final String TAG_PLAN = "Plan";
    private static final String TAG_CHANCE = "Chance";
    private static final String TAG_MIN = "Min";
    private static final String TAG_MAX = "Max";
    private static final String TAG_COOLDOWN_HOURS = "CooldownHours";
    private static final String TAG_LAST_FILLED = "LastFilled";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private NonNullList<ItemStack> plan = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final int[] chance = new int[SIZE];
    private final int[] minCount = new int[SIZE];
    private final int[] maxCount = new int[SIZE];
    private int cooldownHours = -1;
    private long lastFilled;

    private final ChestLidController lidController = new ChestLidController();
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playSound(level, pos, SoundEvents.CHEST_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playSound(level, pos, SoundEvents.CHEST_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
            level.blockEvent(pos, state.getBlock(), 1, current);
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu
                    && menu.getContainer() == DungeonChestBlockEntity.this;
        }
    };

    public DungeonChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUNGEON_CHEST.get(), pos, state);
        for (int slot = 0; slot < SIZE; slot++) {
            chance[slot] = DEFAULT_CHANCE;
            minCount[slot] = 1;
            maxCount[slot] = 1;
        }
    }

    public int configuredCooldownHours() {
        return cooldownHours;
    }

    public int effectiveCooldownHours() {
        return cooldownHours >= 0 ? cooldownHours : ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.getAsInt();
    }

    public long cooldownMillis() {
        return effectiveCooldownHours() * 3_600_000L;
    }

    public long remainingMillis() {
        return Math.max(0L, cooldownMillis() - (DungeonClock.now() - lastFilled));
    }

    public ItemStack planItem(int slot) {
        return slot >= 0 && slot < SIZE ? plan.get(slot) : ItemStack.EMPTY;
    }

    public int chanceAt(int slot) {
        return slot >= 0 && slot < SIZE ? chance[slot] : DEFAULT_CHANCE;
    }

    public int minAt(int slot) {
        return slot >= 0 && slot < SIZE ? minCount[slot] : 1;
    }

    public int maxAt(int slot) {
        return slot >= 0 && slot < SIZE ? maxCount[slot] : 1;
    }

    public int planCount() {
        return (int) plan.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public boolean configured() {
        return planCount() > 0;
    }

    public void setEntry(int slot, int newChance, int newMin, int newMax) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        chance[slot] = Math.clamp(newChance, 0, 100);
        minCount[slot] = Math.clamp(Math.min(newMin, newMax), 1, MAX_COUNT);
        maxCount[slot] = Math.clamp(Math.max(newMin, newMax), 1, MAX_COUNT);
        markUpdated();
    }

    public void setCooldownHours(int hours) {
        cooldownHours = Math.clamp(hours, -1, MAX_COOLDOWN_HOURS);
        markUpdated();
    }

    public void resetCooldown() {
        lastFilled = 0L;
        markUpdated();
    }

    public Container planContainer() {
        return new PlanContainer();
    }

    public boolean refillIfDue() {
        if (!configured() || remainingMillis() > 0L) {
            return false;
        }
        refill();
        return true;
    }

    public void refill() {
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        RandomSource random = level == null ? RandomSource.create() : level.getRandom();
        List<Integer> free = new ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            free.add(slot);
        }
        Collections.shuffle(free, new Random(random.nextLong()));
        int cursor = 0;
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack template = plan.get(slot);
            if (template.isEmpty() || random.nextInt(100) >= chance[slot]) {
                continue;
            }
            int low = Math.min(minCount[slot], maxCount[slot]);
            int high = Math.max(minCount[slot], maxCount[slot]);
            int count = low + random.nextInt(high - low + 1);
            while (count > 0 && cursor < free.size()) {
                int portion = Math.min(count, template.getMaxStackSize());
                items.set(free.get(cursor++), template.copyWithCount(portion));
                count -= portion;
            }
        }
        lastFilled = DungeonClock.now();
        markUpdated();
    }

    public boolean looksEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    private void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    static void playSound(Level level, BlockPos pos, SoundEvent sound) {
        level.playSound(
                null,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                sound,
                SoundSource.BLOCKS,
                0.5F,
                level.random.nextFloat() * 0.1F + 0.9F
        );
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, DungeonChestBlockEntity chest) {
        chest.lidController.tickLid();
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            lidController.shouldBeOpen(type > 0);
            return true;
        }
        return super.triggerEvent(id, type);
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator() && level != null) {
            openersCounter.incrementOpeners(player, level, getBlockPos(), getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator() && level != null) {
            openersCounter.decrementOpeners(player, level, getBlockPos(), getBlockState());
        }
    }

    @Override
    public float getOpenNess(float partialTick) {
        return lidController.getOpenness(partialTick);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> newItems) {
        items = newItems;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.kingdoms.dungeon_chest");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        CompoundTag planTag = new CompoundTag();
        ContainerHelper.saveAllItems(planTag, plan, true, registries);
        tag.put(TAG_PLAN, planTag);
        tag.putIntArray(TAG_CHANCE, chance.clone());
        tag.putIntArray(TAG_MIN, minCount.clone());
        tag.putIntArray(TAG_MAX, maxCount.clone());
        tag.putInt(TAG_COOLDOWN_HOURS, cooldownHours);
        tag.putLong(TAG_LAST_FILLED, lastFilled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        plan = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound(TAG_PLAN), plan, registries);
        readCounts(tag.getIntArray(TAG_CHANCE), chance, DEFAULT_CHANCE, 0, 100);
        readCounts(tag.getIntArray(TAG_MIN), minCount, 1, 1, MAX_COUNT);
        readCounts(tag.getIntArray(TAG_MAX), maxCount, 1, 1, MAX_COUNT);
        cooldownHours = Math.clamp(tag.getInt(TAG_COOLDOWN_HOURS), -1, MAX_COOLDOWN_HOURS);
        lastFilled = tag.getLong(TAG_LAST_FILLED);
    }

    private static void readCounts(int[] stored, int[] target, int fallback, int low, int high) {
        for (int slot = 0; slot < SIZE; slot++) {
            target[slot] = slot < stored.length ? Math.clamp(stored[slot], low, high) : fallback;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag planTag = new CompoundTag();
        ContainerHelper.saveAllItems(planTag, plan, true, registries);
        tag.put(TAG_PLAN, planTag);
        tag.putIntArray(TAG_CHANCE, chance.clone());
        tag.putIntArray(TAG_MIN, minCount.clone());
        tag.putIntArray(TAG_MAX, maxCount.clone());
        tag.putInt(TAG_COOLDOWN_HOURS, cooldownHours);
        tag.putLong(TAG_LAST_FILLED, lastFilled);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private final class PlanContainer extends SimpleContainer {
        private PlanContainer() {
            super(SIZE);
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < SIZE ? plan.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= SIZE) {
                return;
            }
            plan.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            markUpdated();
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack previous = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return previous;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public boolean isEmpty() {
            return plan.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean stillValid(Player player) {
            return !remove;
        }

        @Override
        public void setChanged() {
            markUpdated();
        }
    }
}
