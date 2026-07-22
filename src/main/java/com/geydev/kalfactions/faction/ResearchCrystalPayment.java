package com.geydev.kalfactions.faction;

public interface ResearchCrystalPayment {
    int available(InfluenceType type);

    boolean consumeExact(InfluenceType type, int amount);
}
