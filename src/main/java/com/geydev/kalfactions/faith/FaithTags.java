package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.KalFactions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class FaithTags {
    public static final TagKey<Item> SCIENCE_OFFERINGS_TIER1 = itemTag("science_offerings_tier1");
    public static final TagKey<Item> SCIENCE_OFFERINGS_TIER2 = itemTag("science_offerings_tier2");
    public static final TagKey<Item> SCIENCE_OFFERINGS_TIER3 = itemTag("science_offerings_tier3");
    public static final TagKey<Item> ECONOMY_OFFERINGS = itemTag("economy_offerings");
    public static final TagKey<Item> BOSS_TROPHIES_TIER1 = itemTag("boss_trophies_tier1");
    public static final TagKey<Item> BOSS_TROPHIES_TIER2 = itemTag("boss_trophies_tier2");
    public static final TagKey<Item> BOSS_TROPHIES_TIER3 = itemTag("boss_trophies_tier3");
    public static final TagKey<Block> HIGHLIGHTED_ORES = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "highlighted_ores")
    );

    public static TagKey<Item> scienceOfferings(int tier) {
        return switch (Math.clamp(tier, 1, 3)) {
            case 3 -> SCIENCE_OFFERINGS_TIER3;
            case 2 -> SCIENCE_OFFERINGS_TIER2;
            default -> SCIENCE_OFFERINGS_TIER1;
        };
    }

    public static TagKey<Item> bossTrophies(int tier) {
        return switch (Math.clamp(tier, 1, 3)) {
            case 3 -> BOSS_TROPHIES_TIER3;
            case 2 -> BOSS_TROPHIES_TIER2;
            default -> BOSS_TROPHIES_TIER1;
        };
    }

    public static String tagLabelKey(TagKey<Item> tag) {
        return "kingdoms.faith.offering." + tag.location().getPath();
    }

    public static List<Item> sortedItems(TagKey<Item> tag) {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders -> {
            for (Holder<Item> holder : holders) {
                holder.unwrapKey().ifPresent(key -> items.add(holder.value()));
            }
        });
        items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return items;
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private FaithTags() {
    }
}
