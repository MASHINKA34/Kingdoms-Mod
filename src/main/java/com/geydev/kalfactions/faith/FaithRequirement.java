package com.geydev.kalfactions.faith;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record FaithRequirement(@Nullable Item item, @Nullable TagKey<Item> tag, String labelKey, int count) {
    public FaithRequirement {
        if (item == null && tag == null) {
            throw new IllegalArgumentException("Faith requirement needs an item or a tag");
        }
        labelKey = labelKey == null ? "" : labelKey;
        count = Math.max(0, count);
    }

    public static FaithRequirement ofItem(Item item, int count) {
        return new FaithRequirement(Objects.requireNonNull(item, "item"), null, "", count);
    }

    public static FaithRequirement ofTag(TagKey<Item> tag, String labelKey, int count) {
        return new FaithRequirement(null, Objects.requireNonNull(tag, "tag"), labelKey, count);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return tag == null ? stack.is(item) : stack.is(tag);
    }

    public Item icon() {
        if (item != null) {
            return item;
        }
        return BuiltInRegistries.ITEM.getTag(tag)
                .flatMap(holders -> holders.size() == 0 ? java.util.Optional.empty() : java.util.Optional.of(holders.get(0)))
                .map(net.minecraft.core.Holder::value)
                .orElse(net.minecraft.world.item.Items.BARRIER);
    }

    public Component displayName() {
        return labelKey.isEmpty() ? icon().getDescription() : Component.translatable(labelKey);
    }
}
