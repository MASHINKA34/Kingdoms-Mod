package com.geydev.kalfactions.faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ResearchNode {
    SCI_ROOT(InfluenceType.SCIENCE, null, 40L, 2, ResearchBonus.MINING_SPEED, -320, 0),
    SCI_MINE_1(InfluenceType.SCIENCE, "SCI_ROOT", 80L, 3, ResearchBonus.MINING_SPEED, -190, -110),
    SCI_SMELT(InfluenceType.SCIENCE, "SCI_ROOT", 100L, 4, ResearchBonus.SMELT_SPEED, -200, 10),
    SCI_DRILL_1(InfluenceType.SCIENCE, "SCI_ROOT", 80L, 3, ResearchBonus.DRILL_OUTPUT, -190, 120),
    SCI_MINE_2(InfluenceType.SCIENCE, "SCI_MINE_1", 120L, 4, ResearchBonus.ORE_DROP, -55, -130),
    SCI_LAB(InfluenceType.SCIENCE, "SCI_SMELT", 180L, 6, ResearchBonus.SCIENCE_INFLUENCE, -60, -10),
    SCI_DRILL_2(InfluenceType.SCIENCE, "SCI_DRILL_1", 120L, 4, ResearchBonus.DRILL_INTERVAL, -50, 120),
    SCI_MINE_3(InfluenceType.SCIENCE, "SCI_MINE_2,SCI_DRILL_2", 160L, 5, ResearchBonus.MINING_SPEED, 85, -90),
    SCI_DRILL_3(InfluenceType.SCIENCE, "SCI_DRILL_2,SCI_SMELT", 160L, 5, ResearchBonus.DRILL_OUTPUT, 90, 70),
    SCI_MINE_CAP(InfluenceType.SCIENCE, "SCI_MINE_3", 300L, 8, ResearchBonus.AUTO_SMELT, 225, -130),
    SCI_ACADEMY(InfluenceType.SCIENCE, "SCI_LAB,SCI_MINE_3", 320L, 12, ResearchBonus.CHUNK_SLOT, "SCIENCE_INFLUENCE+CHUNK_SLOT", 225, -15),
    SCI_DRILL_CAP(InfluenceType.SCIENCE, "SCI_DRILL_3,SCI_LAB", 300L, 8, ResearchBonus.DRILL_INTERVAL, 225, 100),

    ECO_ROOT(InfluenceType.ECONOMIC, null, 40L, 2, ResearchBonus.BUY_RATE, -320, 0),
    ECO_TRADE_1(InfluenceType.ECONOMIC, "ECO_ROOT", 80L, 3, ResearchBonus.BUY_RATE, -190, -110),
    ECO_TAX(InfluenceType.ECONOMIC, "ECO_ROOT", 100L, 4, ResearchBonus.TREASURY_INCOME, -200, 10),
    ECO_LAND_1(InfluenceType.ECONOMIC, "ECO_ROOT", 80L, 3, ResearchBonus.CLAIM_DISCOUNT, -190, 120),
    ECO_TRADE_2(InfluenceType.ECONOMIC, "ECO_TRADE_1", 120L, 4, ResearchBonus.BUY_RATE, -55, -130),
    ECO_BANK(InfluenceType.ECONOMIC, "ECO_TAX", 180L, 6, ResearchBonus.ECONOMIC_INFLUENCE, -60, -10),
    ECO_LAND_2(InfluenceType.ECONOMIC, "ECO_LAND_1", 120L, 4, ResearchBonus.CLAIM_DISCOUNT, -50, 120),
    ECO_TRADE_CAP(InfluenceType.ECONOMIC, "ECO_TRADE_2,ECO_BANK", 300L, 8, ResearchBonus.ECONOMIC_INFLUENCE, 90, -120),
    ECO_VAULT(InfluenceType.ECONOMIC, "ECO_BANK,ECO_TAX", 240L, 8, ResearchBonus.TREASURY_INCOME, 85, -10),
    ECO_LAND_3(InfluenceType.ECONOMIC, "ECO_LAND_2,ECO_TRADE_2", 160L, 5, ResearchBonus.CHUNK_SLOT, 95, 110),
    ECO_TREASURY(InfluenceType.ECONOMIC, "ECO_VAULT,ECO_LAND_3", 320L, 12, ResearchBonus.ECONOMIC_INFLUENCE, 235, -50),
    ECO_LAND_CAP(InfluenceType.ECONOMIC, "ECO_LAND_3", 300L, 8, ResearchBonus.CLAIM_DISCOUNT, 235, 90),

    WAR_ROOT(InfluenceType.MILITARY, null, 40L, 2, ResearchBonus.RAID_WARNING, -330, 0),
    WAR_DEF_1(InfluenceType.MILITARY, "WAR_ROOT", 80L, 3, ResearchBonus.RAID_WARNING, -210, -110),
    WAR_WALL_1(InfluenceType.MILITARY, "WAR_ROOT", 80L, 3, ResearchBonus.TNT_RESIST, -215, 10),
    WAR_OFF_1(InfluenceType.MILITARY, "WAR_ROOT", 100L, 4, ResearchBonus.WARRIOR_DAMAGE, -205, 120),
    WAR_DEF_2(InfluenceType.MILITARY, "WAR_DEF_1", 120L, 4, ResearchBonus.FEWER_RAIDERS, -90, -120),
    WAR_WALL_2(InfluenceType.MILITARY, "WAR_WALL_1", 120L, 4, ResearchBonus.TNT_RESIST, -95, 0),
    WAR_OFF_2(InfluenceType.MILITARY, "WAR_OFF_1", 160L, 5, ResearchBonus.WAR_KILL_POINTS, -85, 120),
    WAR_DEF_CAP(InfluenceType.MILITARY, "WAR_DEF_2,WAR_WALL_2", 300L, 8, ResearchBonus.FEWER_RAIDERS, 40, -110),
    WAR_WALL_CAP(InfluenceType.MILITARY, "WAR_WALL_2,WAR_OFF_2", 300L, 8, ResearchBonus.TNT_RESIST, 45, -10),
    WAR_OFF_3(InfluenceType.MILITARY, "WAR_OFF_2", 200L, 6, ResearchBonus.WARRIOR_DAMAGE, 45, 120),
    WAR_OFF_4(InfluenceType.MILITARY, "WAR_OFF_3,WAR_WALL_2", 240L, 8, ResearchBonus.MILITARY_INFLUENCE_RESPAWN, "MILITARY_INFLUENCE", 180, 60),
    WAR_OFF_CAP(InfluenceType.MILITARY, "WAR_OFF_4,WAR_DEF_CAP", 320L, 12, ResearchBonus.MILITARY_INFLUENCE_RESPAWN, "MILITARY_INFLUENCE", 315, -20);

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
