package com.geydev.kalfactions.block;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dungeon.DungeonClock;
import com.geydev.kalfactions.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

public final class DungeonChestBlockEntity extends RandomizableContainerBlockEntity {
    public static final int SIZE = 27;
    public static final int MODE_TEMPLATE = 0;
    public static final int MODE_LOOT_TABLE = 1;
    public static final int MAX_COOLDOWN_HOURS = 8760;

    private static final String TAG_TEMPLATE = "Template";
    private static final String TAG_TEMPLATE_SAVED = "TemplateSaved";
    private static final String TAG_CONFIGURED_TABLE = "ConfiguredLootTable";
    private static final String TAG_MODE = "Mode";
    private static final String TAG_COOLDOWN_HOURS = "CooldownHours";
    private static final String TAG_LAST_FILLED = "LastFilled";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private NonNullList<ItemStack> template = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private ResourceLocation configuredTable;
    private boolean templateSaved;
    private int mode = MODE_TEMPLATE;
    private int cooldownHours = -1;
    private long lastFilled;

    public DungeonChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DUNGEON_CHEST.get(), pos, state);
    }

    public int mode() {
        return mode;
    }

    public int configuredCooldownHours() {
        return cooldownHours;
    }

    public String lootTableId() {
        return configuredTable == null ? "" : configuredTable.toString();
    }

    public int templateCount() {
        return (int) template.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public long cooldownMillis() {
        int hours = cooldownHours >= 0 ? cooldownHours : ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.getAsInt();
        return hours * 3_600_000L;
    }

    public long remainingMillis() {
        return Math.max(0L, cooldownMillis() - (DungeonClock.now() - lastFilled));
    }

    public boolean configured() {
        return mode == MODE_LOOT_TABLE ? configuredTable != null : templateSaved;
    }

    public boolean configure(int newMode, String lootTable, int newCooldownHours) {
        String trimmed = lootTable == null ? "" : lootTable.trim();
        ResourceLocation parsed = trimmed.isEmpty() ? null : ResourceLocation.tryParse(trimmed);
        if (!trimmed.isEmpty() && parsed == null) {
            return false;
        }
        mode = newMode == MODE_LOOT_TABLE ? MODE_LOOT_TABLE : MODE_TEMPLATE;
        cooldownHours = Math.clamp(newCooldownHours, -1, MAX_COOLDOWN_HOURS);
        configuredTable = parsed;
        if (mode == MODE_TEMPLATE) {
            setLootTable(null);
        }
        setChanged();
        return true;
    }

    public void saveTemplate() {
        template = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int slot = 0; slot < SIZE; slot++) {
            template.set(slot, items.get(slot).copy());
        }
        templateSaved = true;
        mode = MODE_TEMPLATE;
        setLootTable(null);
        lastFilled = DungeonClock.now();
        setChanged();
    }

    public void resetCooldown() {
        lastFilled = 0L;
        setChanged();
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
        setLootTable(null);
        if (mode == MODE_LOOT_TABLE && configuredTable != null && level != null) {
            setLootTable(
                    ResourceKey.create(Registries.LOOT_TABLE, configuredTable),
                    level.getRandom().nextLong()
            );
        } else {
            for (int slot = 0; slot < SIZE; slot++) {
                items.set(slot, template.get(slot).copy());
            }
        }
        lastFilled = DungeonClock.now();
        setChanged();
    }

    public boolean looksEmpty() {
        return getLootTable() == null && items.stream().allMatch(ItemStack::isEmpty);
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
        trySaveLootTable(tag);
        ContainerHelper.saveAllItems(tag, items, registries);
        CompoundTag templateTag = new CompoundTag();
        ContainerHelper.saveAllItems(templateTag, template, registries);
        tag.put(TAG_TEMPLATE, templateTag);
        tag.putBoolean(TAG_TEMPLATE_SAVED, templateSaved);
        if (configuredTable != null) {
            tag.putString(TAG_CONFIGURED_TABLE, configuredTable.toString());
        }
        tag.putInt(TAG_MODE, mode);
        tag.putInt(TAG_COOLDOWN_HOURS, cooldownHours);
        tag.putLong(TAG_LAST_FILLED, lastFilled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        tryLoadLootTable(tag);
        ContainerHelper.loadAllItems(tag, items, registries);
        template = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag.getCompound(TAG_TEMPLATE), template, registries);
        templateSaved = tag.getBoolean(TAG_TEMPLATE_SAVED);
        configuredTable = ResourceLocation.tryParse(tag.getString(TAG_CONFIGURED_TABLE));
        mode = tag.getInt(TAG_MODE) == MODE_LOOT_TABLE ? MODE_LOOT_TABLE : MODE_TEMPLATE;
        cooldownHours = Math.clamp(tag.getInt(TAG_COOLDOWN_HOURS), -1, MAX_COOLDOWN_HOURS);
        lastFilled = tag.getLong(TAG_LAST_FILLED);
    }

    public static ResourceKey<LootTable> lootTableKey(ResourceLocation id) {
        return ResourceKey.create(Registries.LOOT_TABLE, id);
    }
}
