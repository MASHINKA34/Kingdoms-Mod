package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.registry.ModBlocks;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public enum ResourceClusterType {
    IRON,
    COPPER,
    GOLD,
    DIAMOND;

    public static ResourceClusterType weighted(int roll) {
        if (roll < 50) {
            return IRON;
        }
        if (roll < 80) {
            return COPPER;
        }
        if (roll < 95) {
            return GOLD;
        }
        return DIAMOND;
    }

    public static Optional<ResourceClusterType> parse(String value) {
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return switch (this) {
            case IRON -> "Железный кластер";
            case COPPER -> "Медный кластер";
            case GOLD -> "Золотой кластер";
            case DIAMOND -> "Алмазный кластер";
        };
    }

    public Block block() {
        return switch (this) {
            case IRON -> ModBlocks.RESOURCE_CLUSTER_IRON.get();
            case COPPER -> ModBlocks.RESOURCE_CLUSTER_COPPER.get();
            case GOLD -> ModBlocks.RESOURCE_CLUSTER_GOLD.get();
            case DIAMOND -> ModBlocks.RESOURCE_CLUSTER_DIAMOND.get();
        };
    }

    public Item displayItem() {
        return switch (this) {
            case IRON -> Items.IRON_INGOT;
            case COPPER -> Items.COPPER_INGOT;
            case GOLD -> Items.GOLD_INGOT;
            case DIAMOND -> Items.DIAMOND;
        };
    }
}
