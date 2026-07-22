package com.geydev.kalfactions.sculk;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class SculkTomePolicy {
    public static final int GUN_BENCH_BLUEPRINT_SLOT = 11;
    public static final ResourceLocation TOME_ID = ResourceLocation.fromNamespaceAndPath("scguns", "sculk_tome");
    public static final TagKey<Item> SCULK_GUNS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "sculk_guns")
    );

    public static boolean isTome(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(TOME_ID);
    }

    public static boolean requiresTome(ItemStack output) {
        return !output.isEmpty() && output.is(SCULK_GUNS);
    }

    public static boolean mayTakeGunBenchResult(Container container, ItemStack output) {
        return !requiresTome(output) || isTome(container.getItem(GUN_BENCH_BLUEPRINT_SLOT));
    }

    public static boolean consumeAfterSuccessfulTake(Container container, ItemStack output) {
        ItemStack tome = container.getItem(GUN_BENCH_BLUEPRINT_SLOT);
        if (!requiresTome(output) && !isTome(tome)) {
            return true;
        }
        if (!isTome(tome)) {
            return false;
        }
        tome.shrink(1);
        container.setItem(GUN_BENCH_BLUEPRINT_SLOT, tome);
        container.setChanged();
        return true;
    }

    private SculkTomePolicy() {
    }
}
