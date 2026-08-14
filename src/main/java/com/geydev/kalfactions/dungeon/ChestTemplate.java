package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public record ChestTemplate(
        UUID id,
        String name,
        String author,
        long createdAt,
        int cooldownHours,
        List<Entry> entries
) {
    public static final int SIZE = DungeonChestBlockEntity.SIZE;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_AUTHOR_LENGTH = 32;

    private static final String TAG_ID = "id";
    private static final String TAG_NAME = "name";
    private static final String TAG_AUTHOR = "author";
    private static final String TAG_CREATED_AT = "createdAt";
    private static final String TAG_COOLDOWN_HOURS = "cooldownHours";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_SLOT = "slot";
    private static final String TAG_ITEM = "item";
    private static final String TAG_CHANCE = "chance";
    private static final String TAG_MIN = "min";
    private static final String TAG_MAX = "max";

    public ChestTemplate {
        id = id == null ? UUID.randomUUID() : id;
        name = normalizeName(name);
        author = normalizeText(author, MAX_AUTHOR_LENGTH);
        cooldownHours = Math.clamp(cooldownHours, -1, DungeonChestBlockEntity.MAX_COOLDOWN_HOURS);
        entries = padded(entries);
    }

    public static ChestTemplate capture(
            UUID id,
            String name,
            String author,
            long createdAt,
            DungeonChestBlockEntity chest
    ) {
        List<Entry> entries = new ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            entries.add(new Entry(
                    chest.planItem(slot).copy(),
                    chest.chanceAt(slot),
                    chest.minAt(slot),
                    chest.maxAt(slot)
            ));
        }
        return new ChestTemplate(id, name, author, createdAt, chest.configuredCooldownHours(), entries);
    }

    public Entry entry(int slot) {
        return slot >= 0 && slot < SIZE ? entries.get(slot) : Entry.EMPTY;
    }

    public int filledSlots() {
        return (int) entries.stream().filter(entry -> !entry.stack().isEmpty()).count();
    }

    public boolean isEmpty() {
        return filledSlots() == 0;
    }

    public ChestTemplate withId(UUID newId) {
        return new ChestTemplate(newId, name, author, createdAt, cooldownHours, entries);
    }

    public ChestTemplate withName(String newName) {
        return new ChestTemplate(id, newName, author, createdAt, cooldownHours, entries);
    }

    public void applyTo(DungeonChestBlockEntity chest, boolean applyCooldown) {
        chest.replacePlan(entries);
        if (applyCooldown) {
            chest.setCooldownHours(cooldownHours);
        }
        chest.resetCooldown();
        chest.refill();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, id);
        tag.putString(TAG_NAME, name);
        tag.putString(TAG_AUTHOR, author);
        tag.putLong(TAG_CREATED_AT, createdAt);
        tag.putInt(TAG_COOLDOWN_HOURS, cooldownHours);
        ListTag list = new ListTag();
        for (int slot = 0; slot < SIZE; slot++) {
            Entry entry = entries.get(slot);
            if (entry.stack().isEmpty()) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putInt(TAG_SLOT, slot);
            entryTag.put(TAG_ITEM, entry.stack().save(registries));
            entryTag.putInt(TAG_CHANCE, entry.chance());
            entryTag.putInt(TAG_MIN, entry.minCount());
            entryTag.putInt(TAG_MAX, entry.maxCount());
            list.add(entryTag);
        }
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    public static Optional<ChestTemplate> load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.hasUUID(TAG_ID)) {
            return Optional.empty();
        }
        List<Entry> entries = new ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            entries.add(Entry.EMPTY);
        }
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entryTag = list.getCompound(index);
            int slot = entryTag.getInt(TAG_SLOT);
            if (slot < 0 || slot >= SIZE) {
                continue;
            }
            ItemStack stack = ItemStack.parse(registries, entryTag.getCompound(TAG_ITEM)).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            entries.set(slot, new Entry(
                    stack,
                    entryTag.getInt(TAG_CHANCE),
                    entryTag.getInt(TAG_MIN),
                    entryTag.getInt(TAG_MAX)
            ));
        }
        ChestTemplate template = new ChestTemplate(
                tag.getUUID(TAG_ID),
                tag.getString(TAG_NAME),
                tag.getString(TAG_AUTHOR),
                tag.getLong(TAG_CREATED_AT),
                tag.getInt(TAG_COOLDOWN_HOURS),
                entries
        );
        return template.name().isEmpty() ? Optional.empty() : Optional.of(template);
    }

    public static String normalizeName(String value) {
        return normalizeText(value, MAX_NAME_LENGTH);
    }

    private static String normalizeText(String value, int maxLength) {
        StringBuilder normalized = new StringBuilder(maxLength);
        if (value != null) {
            value.codePoints()
                    .filter(codePoint -> !Character.isISOControl(codePoint))
                    .limit(maxLength)
                    .forEach(normalized::appendCodePoint);
        }
        return normalized.toString().trim().replaceAll("\\s+", " ");
    }

    private static List<Entry> padded(List<Entry> source) {
        List<Entry> copy = new ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) {
            Entry entry = source != null && slot < source.size() ? source.get(slot) : null;
            copy.add(entry == null ? Entry.EMPTY : entry);
        }
        return List.copyOf(copy);
    }

    public record Entry(ItemStack stack, int chance, int minCount, int maxCount) {
        public static final Entry EMPTY =
                new Entry(ItemStack.EMPTY, DungeonChestBlockEntity.DEFAULT_CHANCE, 1, 1);

        public Entry {
            stack = stack == null ? ItemStack.EMPTY : stack;
            chance = Math.clamp(chance, 0, 100);
            int low = Math.clamp(Math.min(minCount, maxCount), 1, DungeonChestBlockEntity.MAX_COUNT);
            int high = Math.clamp(Math.max(minCount, maxCount), 1, DungeonChestBlockEntity.MAX_COUNT);
            minCount = low;
            maxCount = high;
        }
    }
}
