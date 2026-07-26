package com.geydev.kalfactions.outpost.cluster;

import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum ResourceClusterType {
    SCIENCE(12),
    ECONOMIC(12),
    MILITARY(12),
    DIAMOND(4),
    COAL(12),
    IRON(12),
    COPPER(10),
    ZINC(8),
    GOLD(7),
    LAPIS(6),
    REDSTONE(5);

    private static final ResourceClusterType[] WEIGHT_TABLE = buildWeightTable();

    private final int weight;

    ResourceClusterType(int weight) {
        this.weight = weight;
    }

    public static ResourceClusterType weighted(int roll) {
        int index = Math.floorMod(roll, WEIGHT_TABLE.length);
        return WEIGHT_TABLE[index];
    }

    private static ResourceClusterType[] buildWeightTable() {
        int total = 0;
        for (ResourceClusterType type : values()) {
            total += type.weight;
        }
        ResourceClusterType[] table = new ResourceClusterType[total];
        int cursor = 0;
        for (ResourceClusterType type : values()) {
            for (int index = 0; index < type.weight; index++) {
                table[cursor++] = type;
            }
        }
        return table;
    }

    public static Optional<ResourceClusterType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean isCrystal() {
        return this == SCIENCE || this == ECONOMIC || this == MILITARY;
    }

    public Optional<InfluenceType> influenceType() {
        return switch (this) {
            case SCIENCE -> Optional.of(InfluenceType.SCIENCE);
            case ECONOMIC -> Optional.of(InfluenceType.ECONOMIC);
            case MILITARY -> Optional.of(InfluenceType.MILITARY);
            default -> Optional.empty();
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
            case COAL -> "Угольный кластер";
            case IRON -> "Железный кластер";
            case COPPER -> "Медный кластер";
            case ZINC -> "Цинковый кластер";
            case GOLD -> "Золотой кластер";
            case LAPIS -> "Лазуритовый кластер";
            case REDSTONE -> "Редстоуновый кластер";
        };
    }

    public Block block() {
        return switch (this) {
            case SCIENCE -> ModBlocks.RESOURCE_CLUSTER_SCIENCE.get();
            case ECONOMIC -> ModBlocks.RESOURCE_CLUSTER_ECONOMIC.get();
            case MILITARY -> ModBlocks.RESOURCE_CLUSTER_MILITARY.get();
            case DIAMOND -> ModBlocks.RESOURCE_CLUSTER_DIAMOND.get();
            case COAL -> Blocks.COAL_ORE;
            case IRON -> Blocks.IRON_ORE;
            case COPPER -> Blocks.COPPER_ORE;
            case ZINC -> createBlock("zinc_ore", Blocks.IRON_ORE);
            case GOLD -> Blocks.GOLD_ORE;
            case LAPIS -> Blocks.LAPIS_ORE;
            case REDSTONE -> Blocks.REDSTONE_ORE;
        };
    }

    public Item displayItem() {
        return switch (this) {
            case SCIENCE -> ModItems.crystalFor(InfluenceType.SCIENCE);
            case ECONOMIC -> ModItems.crystalFor(InfluenceType.ECONOMIC);
            case MILITARY -> ModItems.crystalFor(InfluenceType.MILITARY);
            case DIAMOND -> Items.DIAMOND;
            case COAL -> Items.COAL;
            case IRON -> Items.RAW_IRON;
            case COPPER -> Items.RAW_COPPER;
            case ZINC -> createItem("raw_zinc", Items.RAW_IRON);
            case GOLD -> Items.RAW_GOLD;
            case LAPIS -> Items.LAPIS_LAZULI;
            case REDSTONE -> Items.REDSTONE;
        };
    }

    private static Block createBlock(String path, Block fallback) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
        return block == Blocks.AIR ? fallback : block;
    }

    private static Item createItem(String path, Item fallback) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", path));
        return item == Items.AIR ? fallback : item;
    }
}
