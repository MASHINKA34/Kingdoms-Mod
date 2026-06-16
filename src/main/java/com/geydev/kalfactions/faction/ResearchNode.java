package com.geydev.kalfactions.faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ResearchNode {
    SCIENCE_1(InfluenceType.SCIENCE, 1, ResearchBonus.MINING_SPEED),
    SCIENCE_2(InfluenceType.SCIENCE, 2, ResearchBonus.DRILL_OUTPUT),
    SCIENCE_3(InfluenceType.SCIENCE, 3, ResearchBonus.SMELT_SPEED),
    SCIENCE_4(InfluenceType.SCIENCE, 4, ResearchBonus.SCIENCE_CHUNK_SLOT),
    SCIENCE_5(InfluenceType.SCIENCE, 5, ResearchBonus.SCIENCE_INFLUENCE),
    ECONOMIC_1(InfluenceType.ECONOMIC, 1, ResearchBonus.BUY_RATE),
    ECONOMIC_2(InfluenceType.ECONOMIC, 2, ResearchBonus.CLAIM_DISCOUNT),
    ECONOMIC_3(InfluenceType.ECONOMIC, 3, ResearchBonus.ECONOMIC_CHUNK_SLOT),
    ECONOMIC_4(InfluenceType.ECONOMIC, 4, ResearchBonus.TREASURY_INCOME),
    ECONOMIC_5(InfluenceType.ECONOMIC, 5, ResearchBonus.ECONOMIC_INFLUENCE),
    MILITARY_1(InfluenceType.MILITARY, 1, ResearchBonus.RAID_WARNING),
    MILITARY_2(InfluenceType.MILITARY, 2, ResearchBonus.CLAIM_TNT_RESIST),
    MILITARY_3(InfluenceType.MILITARY, 3, ResearchBonus.FEWER_RAIDERS),
    MILITARY_4(InfluenceType.MILITARY, 4, ResearchBonus.WAR_KILL_POINTS),
    MILITARY_5(InfluenceType.MILITARY, 5, ResearchBonus.MILITARY_INFLUENCE_RESPAWN);

    public static final int NODES_PER_BRANCH = 5;
    private static final long[] COST_BY_TIER = {0L, 50L, 100L, 150L, 200L, 300L};
    private static final int[] DURATION_HOURS_BY_TIER = {0, 2, 4, 6, 8, 12};

    private final InfluenceType type;
    private final int tier;
    private final ResearchBonus bonus;

    ResearchNode(InfluenceType type, int tier, ResearchBonus bonus) {
        this.type = type;
        this.tier = tier;
        this.bonus = bonus;
    }

    public InfluenceType type() {
        return type;
    }

    public int tier() {
        return tier;
    }

    public ResearchBonus bonus() {
        return bonus;
    }

    public long cost() {
        return COST_BY_TIER[tier];
    }

    public int durationHours() {
        return DURATION_HOURS_BY_TIER[tier];
    }

    public long durationMillis() {
        return durationHours() * 3_600_000L;
    }

    public Optional<ResearchNode> prerequisite() {
        return tier <= 1 ? Optional.empty() : nodeFor(type, tier - 1);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "kingdoms.research." + id();
    }

    public String descriptionKey() {
        return "kingdoms.research." + id() + ".desc";
    }

    public static Optional<ResearchNode> nodeFor(InfluenceType type, int tier) {
        for (ResearchNode node : values()) {
            if (node.type == type && node.tier == tier) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    public static List<ResearchNode> branch(InfluenceType type) {
        List<ResearchNode> nodes = new ArrayList<>();
        for (int tier = 1; tier <= NODES_PER_BRANCH; tier++) {
            nodeFor(type, tier).ifPresent(nodes::add);
        }
        return nodes;
    }

    public static Optional<ResearchNode> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
