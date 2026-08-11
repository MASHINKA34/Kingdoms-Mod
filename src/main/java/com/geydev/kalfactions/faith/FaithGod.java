package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.registry.ModBlocks;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public enum FaithGod {
    SCIENCE(InfluenceType.SCIENCE),
    WAR(InfluenceType.MILITARY),
    ECONOMY(InfluenceType.ECONOMIC);

    public static final FaithGod[] VALUES = values();
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    private final InfluenceType influenceType;

    FaithGod(InfluenceType influenceType) {
        this.influenceType = influenceType;
    }

    public int index() {
        return ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public InfluenceType influenceType() {
        return influenceType;
    }

    public Item crystal() {
        return com.geydev.kalfactions.registry.ModItems.crystalFor(influenceType);
    }

    public String translationKey() {
        return "kingdoms.faith.god." + id();
    }

    public static Optional<FaithGod> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static Optional<FaithGod> byIndex(int index) {
        return index < 0 || index >= VALUES.length ? Optional.empty() : Optional.of(VALUES[index]);
    }

    public static Optional<FaithGod> ofStatue(BlockState state) {
        return state == null ? Optional.empty() : ofStatue(state.getBlock());
    }

    public static Optional<FaithGod> ofStatue(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        if (block == ModBlocks.STATUE_SCIENCE.get() || block == ModBlocks.RESEARCH_GOD_STONE_8BLOCKS.get()) {
            return Optional.of(SCIENCE);
        }
        if (block == ModBlocks.WAR_GOD_STATUE.get() || block == ModBlocks.WAR_GOD_STONE_8BLOCKS.get()) {
            return Optional.of(WAR);
        }
        if (block == ModBlocks.ECONOMY_GOD_STATUE.get() || block == ModBlocks.ECONOMY_GOD_STONE_8BLOCKS.get()) {
            return Optional.of(ECONOMY);
        }
        return Optional.empty();
    }

    public static boolean isGreatStatue(Block block) {
        return block == ModBlocks.RESEARCH_GOD_STONE_8BLOCKS.get()
                || block == ModBlocks.WAR_GOD_STONE_8BLOCKS.get()
                || block == ModBlocks.ECONOMY_GOD_STONE_8BLOCKS.get();
    }

    public static boolean isSmallStatue(Block block) {
        return block == ModBlocks.STATUE_SCIENCE.get()
                || block == ModBlocks.WAR_GOD_STATUE.get()
                || block == ModBlocks.ECONOMY_GOD_STATUE.get();
    }
}
