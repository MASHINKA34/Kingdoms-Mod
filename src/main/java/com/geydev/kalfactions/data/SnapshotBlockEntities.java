package com.geydev.kalfactions.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SnapshotBlockEntities {
    public static BlockEntity loadEmpty(
            BlockPos pos, BlockState state, CompoundTag saved, HolderLookup.Provider registries
    ) {
        CompoundTag clean = saved.copy();
        clean.remove("LootTable");
        clean.remove("LootTableSeed");
        BlockEntity entity = BlockEntity.loadStatic(pos, state, clean, registries);
        if (entity instanceof Clearable clearable) {
            clearable.clearContent();
            return entity;
        }
        CompoundTag sanitized = new CompoundTag();
        for (String key : clean.getAllKeys()) {
            sanitized.put(key, withoutItems(clean.get(key)));
        }
        return BlockEntity.loadStatic(pos, state, sanitized, registries);
    }

    private static Tag withoutItems(Tag value) {
        if (value instanceof CompoundTag compound) {
            if (compound.contains("id", Tag.TAG_STRING)
                    && (compound.contains("count", Tag.TAG_ANY_NUMERIC)
                        || compound.contains("Count", Tag.TAG_ANY_NUMERIC))) {
                ResourceLocation item = ResourceLocation.tryParse(compound.getString("id"));
                if (item != null && BuiltInRegistries.ITEM.containsKey(item)) {
                    return new CompoundTag();
                }
            }
            CompoundTag copy = new CompoundTag();
            for (String key : compound.getAllKeys()) {
                copy.put(key, withoutItems(compound.get(key)));
            }
            return copy;
        }
        if (value instanceof ListTag list) {
            ListTag copy = new ListTag();
            for (Tag entry : list) {
                copy.add(withoutItems(entry));
            }
            return copy;
        }
        return value.copy();
    }

    private SnapshotBlockEntities() {
    }
}
