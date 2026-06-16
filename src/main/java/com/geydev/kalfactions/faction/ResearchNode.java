package com.geydev.kalfactions.faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ResearchNode {
    SCI_ROOT(InfluenceType.SCIENCE, null, 40L, 2, ResearchBonus.MINING_SPEED, 0, 0),
    SCI_MINE_1(InfluenceType.SCIENCE, "SCI_ROOT", 80L, 3, ResearchBonus.MINING_SPEED, -100, -54),
    SCI_MINE_2(InfluenceType.SCIENCE, "SCI_MINE_1", 120L, 4, ResearchBonus.ORE_DROP, -190, -88),
    SCI_MINE_3(InfluenceType.SCIENCE, "SCI_MINE_2", 160L, 5, ResearchBonus.MINING_SPEED, -280, -124),
    SCI_MINE_CAP(InfluenceType.SCIENCE, "SCI_MINE_3", 300L, 8, ResearchBonus.AUTO_SMELT, -370, -158),
    SCI_DRILL_1(InfluenceType.SCIENCE, "SCI_ROOT", 80L, 3, ResearchBonus.DRILL_OUTPUT, -100, 28),
    SCI_DRILL_2(InfluenceType.SCIENCE, "SCI_DRILL_1", 120L, 4, ResearchBonus.DRILL_INTERVAL, -195, 56),
    SCI_DRILL_3(InfluenceType.SCIENCE, "SCI_DRILL_2", 160L, 5, ResearchBonus.DRILL_OUTPUT, -290, 84),
    SCI_DRILL_CAP(InfluenceType.SCIENCE, "SCI_DRILL_3", 300L, 8, ResearchBonus.DRILL_INTERVAL, -385, 114),
    SCI_SMELT(InfluenceType.SCIENCE, "SCI_ROOT", 100L, 4, ResearchBonus.SMELT_SPEED, 105, -48),
    SCI_LAB(InfluenceType.SCIENCE, "SCI_SMELT", 180L, 6, ResearchBonus.SCIENCE_INFLUENCE, 205, -70),
    SCI_ACADEMY(InfluenceType.SCIENCE, "SCI_LAB", 320L, 12, ResearchBonus.CHUNK_SLOT, "SCIENCE_INFLUENCE+CHUNK_SLOT", 310, -92),

    ECO_ROOT(InfluenceType.ECONOMIC, null, 40L, 2, ResearchBonus.BUY_RATE, 0, 0),
    ECO_TRADE_1(InfluenceType.ECONOMIC, "ECO_ROOT", 80L, 3, ResearchBonus.BUY_RATE, -105, -56),
    ECO_TRADE_2(InfluenceType.ECONOMIC, "ECO_TRADE_1", 120L, 4, ResearchBonus.BUY_RATE, -205, -88),
    ECO_TRADE_CAP(InfluenceType.ECONOMIC, "ECO_TRADE_2", 300L, 8, ResearchBonus.ECONOMIC_INFLUENCE, -310, -118),
    ECO_LAND_1(InfluenceType.ECONOMIC, "ECO_ROOT", 80L, 3, ResearchBonus.CLAIM_DISCOUNT, -100, 28),
    ECO_LAND_2(InfluenceType.ECONOMIC, "ECO_LAND_1", 120L, 4, ResearchBonus.CLAIM_DISCOUNT, -195, 56),
    ECO_LAND_3(InfluenceType.ECONOMIC, "ECO_LAND_2", 160L, 5, ResearchBonus.CHUNK_SLOT, -290, 84),
    ECO_LAND_CAP(InfluenceType.ECONOMIC, "ECO_LAND_3", 300L, 8, ResearchBonus.CLAIM_DISCOUNT, -390, 114),
    ECO_TAX(InfluenceType.ECONOMIC, "ECO_ROOT", 100L, 4, ResearchBonus.TREASURY_INCOME, 105, -48),
    ECO_BANK(InfluenceType.ECONOMIC, "ECO_TAX", 180L, 6, ResearchBonus.ECONOMIC_INFLUENCE, 205, -68),
    ECO_VAULT(InfluenceType.ECONOMIC, "ECO_BANK", 240L, 8, ResearchBonus.TREASURY_INCOME, 305, -88),
    ECO_TREASURY(InfluenceType.ECONOMIC, "ECO_VAULT", 320L, 12, ResearchBonus.ECONOMIC_INFLUENCE, 405, -108),

    WAR_ROOT(InfluenceType.MILITARY, null, 40L, 2, ResearchBonus.RAID_WARNING, 0, 0),
    WAR_DEF_1(InfluenceType.MILITARY, "WAR_ROOT", 80L, 3, ResearchBonus.RAID_WARNING, -105, -58),
    WAR_DEF_2(InfluenceType.MILITARY, "WAR_DEF_1", 120L, 4, ResearchBonus.FEWER_RAIDERS, -205, -92),
    WAR_DEF_CAP(InfluenceType.MILITARY, "WAR_DEF_2", 300L, 8, ResearchBonus.FEWER_RAIDERS, -310, -126),
    WAR_WALL_1(InfluenceType.MILITARY, "WAR_ROOT", 80L, 3, ResearchBonus.TNT_RESIST, -102, 30),
    WAR_WALL_2(InfluenceType.MILITARY, "WAR_WALL_1", 120L, 4, ResearchBonus.TNT_RESIST, -200, 62),
    WAR_WALL_CAP(InfluenceType.MILITARY, "WAR_WALL_2", 300L, 8, ResearchBonus.TNT_RESIST, -304, 96),
    WAR_OFF_1(InfluenceType.MILITARY, "WAR_ROOT", 100L, 4, ResearchBonus.WARRIOR_DAMAGE, 105, -44),
    WAR_OFF_2(InfluenceType.MILITARY, "WAR_OFF_1", 160L, 5, ResearchBonus.WAR_KILL_POINTS, 205, -62),
    WAR_OFF_3(InfluenceType.MILITARY, "WAR_OFF_2", 200L, 6, ResearchBonus.WARRIOR_DAMAGE, 305, -80),
    WAR_OFF_4(InfluenceType.MILITARY, "WAR_OFF_3", 240L, 8, ResearchBonus.MILITARY_INFLUENCE_RESPAWN, "MILITARY_INFLUENCE", 405, -98),
    WAR_OFF_CAP(InfluenceType.MILITARY, "WAR_OFF_4", 320L, 12, ResearchBonus.MILITARY_INFLUENCE_RESPAWN, "MILITARY_INFLUENCE", 505, -116);

    public static final int MAX_NODES_PER_BRANCH = 13;

    private final InfluenceType type;
    private final String prerequisiteName;
    private final long cost;
    private final int durationHours;
    private final ResearchBonus bonus;
    private final String bonusTag;
    private final int treeX;
    private final int treeY;

    ResearchNode(
            InfluenceType type,
            String prerequisiteName,
            long cost,
            int durationHours,
            ResearchBonus bonus,
            int treeX,
            int treeY
    ) {
        this(type, prerequisiteName, cost, durationHours, bonus, bonus.name(), treeX, treeY);
    }

    ResearchNode(
            InfluenceType type,
            String prerequisiteName,
            long cost,
            int durationHours,
            ResearchBonus bonus,
            String bonusTag,
            int treeX,
            int treeY
    ) {
        this.type = type;
        this.prerequisiteName = prerequisiteName;
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
        int tier = 1;
        Optional<ResearchNode> parent = prerequisite();
        while (parent.isPresent()) {
            tier++;
            parent = parent.get().prerequisite();
        }
        return tier;
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

    public Optional<ResearchNode> prerequisite() {
        return prerequisiteName == null ? Optional.empty() : parse(prerequisiteName);
    }

    public boolean root() {
        return prerequisiteName == null;
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
            return legacy(value);
        }
    }

    private static Optional<ResearchNode> legacy(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SCIENCE_1" -> Optional.of(SCI_ROOT);
            case "SCIENCE_2" -> Optional.of(SCI_DRILL_1);
            case "SCIENCE_3" -> Optional.of(SCI_SMELT);
            case "SCIENCE_4" -> Optional.of(SCI_ACADEMY);
            case "SCIENCE_5" -> Optional.of(SCI_LAB);
            case "ECONOMIC_1" -> Optional.of(ECO_ROOT);
            case "ECONOMIC_2" -> Optional.of(ECO_LAND_1);
            case "ECONOMIC_3" -> Optional.of(ECO_LAND_3);
            case "ECONOMIC_4" -> Optional.of(ECO_TAX);
            case "ECONOMIC_5" -> Optional.of(ECO_BANK);
            case "MILITARY_1" -> Optional.of(WAR_ROOT);
            case "MILITARY_2" -> Optional.of(WAR_WALL_1);
            case "MILITARY_3" -> Optional.of(WAR_DEF_2);
            case "MILITARY_4" -> Optional.of(WAR_OFF_2);
            case "MILITARY_5" -> Optional.of(WAR_OFF_4);
            default -> Optional.empty();
        };
    }
}
