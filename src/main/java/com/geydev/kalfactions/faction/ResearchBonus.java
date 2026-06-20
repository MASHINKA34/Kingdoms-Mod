package com.geydev.kalfactions.faction;

import java.util.Locale;

public enum ResearchBonus {
    MINING_SPEED(InfluenceType.SCIENCE),
    ORE_DROP(InfluenceType.SCIENCE),
    DRILL_OUTPUT(InfluenceType.SCIENCE),
    DRILL_INTERVAL(InfluenceType.SCIENCE),
    SMELT_SPEED(InfluenceType.SCIENCE),
    CRAFT_EXTRA(InfluenceType.SCIENCE),
    ENCHANT_BOOST(InfluenceType.SCIENCE),
    CHUNK_SLOT(InfluenceType.SCIENCE),
    SCIENCE_CHUNK_SLOT(InfluenceType.SCIENCE),
    SCIENCE_INFLUENCE(InfluenceType.SCIENCE),
    BUY_RATE(InfluenceType.ECONOMIC),
    CLAIM_DISCOUNT(InfluenceType.ECONOMIC),
    VILLAGER_DISCOUNT(InfluenceType.ECONOMIC),
    VILLAGER_EXTRA(InfluenceType.ECONOMIC),
    OUTPOST_DISCOUNT(InfluenceType.ECONOMIC),
    RAID_STEAL_RESIST(InfluenceType.ECONOMIC),
    ECONOMIC_CHUNK_SLOT(InfluenceType.ECONOMIC),
    TREASURY_INCOME(InfluenceType.ECONOMIC),
    ECONOMIC_INFLUENCE(InfluenceType.ECONOMIC),
    RAID_WARNING(InfluenceType.MILITARY),
    TNT_RESIST(InfluenceType.MILITARY),
    CLAIM_TNT_RESIST(InfluenceType.MILITARY),
    FEWER_RAIDERS(InfluenceType.MILITARY),
    WARRIOR_DAMAGE(InfluenceType.MILITARY),
    ARMOR_BOOST(InfluenceType.MILITARY),
    RAID_REWARD(InfluenceType.MILITARY),
    WAR_KILL_POINTS(InfluenceType.MILITARY),
    MILITARY_INFLUENCE_RESPAWN(InfluenceType.MILITARY);

    private final InfluenceType type;

    ResearchBonus(InfluenceType type) {
        this.type = type;
    }

    public InfluenceType type() {
        return type;
    }

    public boolean isChunkSlot() {
        return this == CHUNK_SLOT || this == SCIENCE_CHUNK_SLOT || this == ECONOMIC_CHUNK_SLOT;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
