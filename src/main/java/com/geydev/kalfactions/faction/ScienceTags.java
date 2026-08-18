package com.geydev.kalfactions.faction;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ScienceTags {
    public static final TagKey<Item> DISCOVERY_TIER1 = itemTag("science_discovery_tier1");
    public static final TagKey<Item> DISCOVERY_TIER2 = itemTag("science_discovery_tier2");
    public static final TagKey<Item> DISCOVERY_TIER3 = itemTag("science_discovery_tier3");

    public static int discoveryTier(ItemStack stack) {
        if (stack.is(DISCOVERY_TIER3)) {
            return 3;
        }
        if (stack.is(DISCOVERY_TIER2)) {
            return 2;
        }
        return stack.is(DISCOVERY_TIER1) ? 1 : 0;
    }

    public static double discoveryMultiplier(int tier) {
        return switch (tier) {
            case 3 -> ModConfigSpec.SCIENCE_DISCOVERY_TIER3_MULTIPLIER.get();
            case 2 -> ModConfigSpec.SCIENCE_DISCOVERY_TIER2_MULTIPLIER.get();
            case 1 -> ModConfigSpec.SCIENCE_DISCOVERY_TIER1_MULTIPLIER.get();
            default -> 1.0D;
        };
    }

    public static double discoveryMultiplier(ItemStack stack) {
        return discoveryMultiplier(discoveryTier(stack));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, path));
    }

    private ScienceTags() {
    }
}
