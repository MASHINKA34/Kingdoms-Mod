package com.geydev.kalfactions.faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ResearchNode {
    SCI_SMELT(InfluenceType.SCIENCE, null, 900L, 2, ResearchBonus.SMELT_SPEED, -330, 10),
    SCI_MINE_1(InfluenceType.SCIENCE, "SCI_SMELT", 1200L, 3, ResearchBonus.MINING_SPEED, -210, -70),
    SCI_DRILL_SPD_1(InfluenceType.SCIENCE, "SCI_SMELT", 1200L, 3, ResearchBonus.DRILL_INTERVAL, -205, 90),
    SCI_MINE_2(InfluenceType.SCIENCE, "SCI_MINE_1", 1800L, 4, ResearchBonus.MINING_SPEED, -90, -70),
    SCI_DRILL_OUT_1(InfluenceType.SCIENCE, "SCI_DRILL_SPD_1", 1600L, 4, ResearchBonus.DRILL_OUTPUT, -80, 90),
    SCI_MINE_3(InfluenceType.SCIENCE, "SCI_MINE_2", 2200L, 5, ResearchBonus.MINING_SPEED, 10, -130),
    SCI_CHUNK(InfluenceType.SCIENCE, "SCI_MINE_2,SCI_DRILL_OUT_1", 2000L, 5, ResearchBonus.CHUNK_SLOT, 30, -30),
    SCI_DRILL_SPD_2(InfluenceType.SCIENCE, "SCI_DRILL_OUT_1", 2000L, 5, ResearchBonus.DRILL_INTERVAL, 60, 90),
    SCI_ORE(InfluenceType.SCIENCE, "SCI_CHUNK", 3000L, 6, ResearchBonus.ORE_DROP, 150, -110),
    SCI_ENCHANT(InfluenceType.SCIENCE, "SCI_CHUNK,SCI_DRILL_SPD_2", 3400L, 6, ResearchBonus.ENCHANT_BOOST, 160, 0),
    SCI_DRILL_OUT_2(InfluenceType.SCIENCE, "SCI_DRILL_SPD_2", 2200L, 6, ResearchBonus.DRILL_OUTPUT, 190, 90),
    SCI_CRAFT(InfluenceType.SCIENCE, "SCI_ORE,SCI_ENCHANT", 3500L, 8, ResearchBonus.CRAFT_EXTRA, 300, -60),

    ECO_BUY_1(InfluenceType.ECONOMIC, null, 300L, 2, ResearchBonus.BUY_RATE, -330, 0),
    ECO_BUY_2(InfluenceType.ECONOMIC, "ECO_BUY_1", 400L, 3, ResearchBonus.BUY_RATE, -200, -60),
    ECO_STEAL_1(InfluenceType.ECONOMIC, "ECO_BUY_1", 400L, 3, ResearchBonus.RAID_STEAL_RESIST, -205, 80),
    ECO_BUY_3(InfluenceType.ECONOMIC, "ECO_BUY_2", 600L, 4, ResearchBonus.BUY_RATE, -40, -60),
    ECO_OUTPOST(InfluenceType.ECONOMIC, "ECO_BUY_2,ECO_STEAL_1", 700L, 4, ResearchBonus.OUTPOST_DISCOUNT, -50, 20),
    ECO_STEAL_2(InfluenceType.ECONOMIC, "ECO_STEAL_1", 600L, 4, ResearchBonus.RAID_STEAL_RESIST, -30, 90),
    ECO_CHUNK(InfluenceType.ECONOMIC, "ECO_BUY_3", 700L, 5, ResearchBonus.CHUNK_SLOT, 110, -90),
    ECO_CLAIM(InfluenceType.ECONOMIC, "ECO_BUY_3", 900L, 5, ResearchBonus.CLAIM_DISCOUNT, 120, -20),
    ECO_STEAL_3(InfluenceType.ECONOMIC, "ECO_STEAL_2,ECO_OUTPOST", 900L, 5, ResearchBonus.RAID_STEAL_RESIST, 130, 90),
    ECO_VILL_DISC(InfluenceType.ECONOMIC, "ECO_CHUNK,ECO_CLAIM", 1200L, 6, ResearchBonus.VILLAGER_DISCOUNT, 290, -55),
    ECO_VILL_EXTRA(InfluenceType.ECONOMIC, "ECO_STEAL_3", 1200L, 6, ResearchBonus.VILLAGER_EXTRA, 290, 60),

    WAR_WARN_1(InfluenceType.MILITARY, null, 450L, 2, ResearchBonus.RAID_WARNING, -340, 0),
    WAR_REWARD_1(InfluenceType.MILITARY, "WAR_WARN_1", 500L, 3, ResearchBonus.RAID_REWARD, -215, -65),
    WAR_MOB_1(InfluenceType.MILITARY, "WAR_WARN_1", 500L, 3, ResearchBonus.WARRIOR_DAMAGE, -210, 70),
    WAR_FEWER(InfluenceType.MILITARY, "WAR_REWARD_1,WAR_MOB_1", 600L, 4, ResearchBonus.FEWER_RAIDERS, -90, -75),
    WAR_MOB_2(InfluenceType.MILITARY, "WAR_MOB_1", 600L, 4, ResearchBonus.WARRIOR_DAMAGE, -80, 70),
    WAR_ARMOR(InfluenceType.MILITARY, "WAR_FEWER,WAR_MOB_2", 800L, 5, ResearchBonus.ARMOR_BOOST, 20, -10),
    WAR_CHUNK(InfluenceType.MILITARY, "WAR_MOB_2", 700L, 5, ResearchBonus.CHUNK_SLOT, 40, 90),
    WAR_TNT(InfluenceType.MILITARY, "WAR_ARMOR", 950L, 6, ResearchBonus.TNT_RESIST, 140, -85),
    WAR_WARN_2(InfluenceType.MILITARY, "WAR_ARMOR", 1100L, 6, ResearchBonus.RAID_WARNING, 150, 60),
    WAR_REWARD_2(InfluenceType.MILITARY, "WAR_TNT", 1200L, 8, ResearchBonus.RAID_REWARD, 260, -75),
    WAR_MOB_3(InfluenceType.MILITARY, "WAR_REWARD_2,WAR_WARN_2", 1600L, 10, ResearchBonus.WARRIOR_DAMAGE, 320, -5);

    public static final int MAX_NODES_PER_BRANCH = 13;

    private final InfluenceType type;
    private final String prerequisiteNames;
    private final long cost;
    private final int durationHours;
    private final ResearchBonus bonus;
    private final String bonusTag;
    private final int treeX;
    private final int treeY;

    ResearchNode(
            InfluenceType type,
            String prerequisiteNames,
            long cost,
            int durationHours,
            ResearchBonus bonus,
            int treeX,
            int treeY
    ) {
        this(type, prerequisiteNames, cost, durationHours, bonus, bonus.name(), treeX, treeY);
    }

    ResearchNode(
            InfluenceType type,
            String prerequisiteNames,
            long cost,
            int durationHours,
            ResearchBonus bonus,
            String bonusTag,
            int treeX,
            int treeY
    ) {
        this.type = type;
        this.prerequisiteNames = prerequisiteNames;
        this.cost = cost;
        this.durationHours = durationHours;
        this.bonus = bonus;
        this.bonusTag = bonusTag;
        this.treeX = treeX;
        this.treeY = treeY;
    }

    public InfluenceType type() {
        return type;
    }

    public int tier() {
        int max = 0;
        for (ResearchNode parent : prerequisites()) {
            max = Math.max(max, parent.tier());
        }
        return max + 1;
    }

    public ResearchBonus bonus() {
        return bonus;
    }

    public String bonusTag() {
        return bonusTag;
    }

    public long cost() {
        return cost;
    }

    public int durationHours() {
        return durationHours;
    }

    public long durationMillis() {
        return durationHours() * 3_600_000L;
    }

    public List<ResearchNode> prerequisites() {
        if (prerequisiteNames == null) {
            return List.of();
        }
        List<ResearchNode> nodes = new ArrayList<>();
        for (String part : prerequisiteNames.split(",")) {
            parse(part.trim()).ifPresent(nodes::add);
        }
        return List.copyOf(nodes);
    }

    public boolean root() {
        return prerequisiteNames == null;
    }

    public int treeX() {
        return treeX;
    }

    public int treeY() {
        return treeY;
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
        return branch(type).stream().filter(node -> node.tier() == tier).findFirst();
    }

    public static List<ResearchNode> branch(InfluenceType type) {
        List<ResearchNode> nodes = new ArrayList<>();
        for (ResearchNode node : values()) {
            if (node.type == type) {
                nodes.add(node);
            }
        }
        return List.copyOf(nodes);
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
