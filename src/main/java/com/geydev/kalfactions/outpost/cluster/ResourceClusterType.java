package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public enum ResourceClusterType {
    SCIENCE,
    ECONOMIC,
    MILITARY,
    DIAMOND;

    public static ResourceClusterType weighted(int roll) {
        if (roll < 31) {
            return SCIENCE;
        }
        if (roll < 62) {
            return ECONOMIC;
        }
        if (roll < 93) {
            return MILITARY;
        }
        return DIAMOND;
    }

    public static Optional<ResourceClusterType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String upper = value.toUpperCase(Locale.ROOT);
        switch (upper) {
            case "IRON":
                return Optional.of(SCIENCE);
            case "COPPER":
                return Optional.of(ECONOMIC);
            case "GOLD":
                return Optional.of(MILITARY);
            default:
                try {
                    return Optional.of(valueOf(upper));
                } catch (IllegalArgumentException exception) {
                    return Optional.empty();
                }
        }
    }

    public Optional<InfluenceType> influenceType() {
        return switch (this) {
            case SCIENCE -> Optional.of(InfluenceType.SCIENCE);
            case ECONOMIC -> Optional.of(InfluenceType.ECONOMIC);
            case MILITARY -> Optional.of(InfluenceType.MILITARY);
            case DIAMOND -> Optional.empty();
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return switch (this) {
            case SCIENCE -> "Кластер науки";
            case ECONOMIC -> "Кластер экономики";
            case MILITARY -> "Кластер войны";
            case DIAMOND -> "Алмазный кластер";
        };
    }

    public Block block() {
        return switch (this) {
            case SCIENCE -> ModBlocks.RESOURCE_CLUSTER_SCIENCE.get();
            case ECONOMIC -> ModBlocks.RESOURCE_CLUSTER_ECONOMIC.get();
            case MILITARY -> ModBlocks.RESOURCE_CLUSTER_MILITARY.get();
            case DIAMOND -> ModBlocks.RESOURCE_CLUSTER_DIAMOND.get();
        };
    }

    public Item displayItem() {
        return switch (this) {
            case SCIENCE -> ModItems.crystalFor(InfluenceType.SCIENCE);
            case ECONOMIC -> ModItems.crystalFor(InfluenceType.ECONOMIC);
            case MILITARY -> ModItems.crystalFor(InfluenceType.MILITARY);
            case DIAMOND -> Items.DIAMOND;
        };
    }
}
