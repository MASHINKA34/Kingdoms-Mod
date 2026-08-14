package com.geydev.kalfactions.faction;

public final class ResearchCosts {
    public static long discounted(long cost, double discount) {
        if (cost <= 0L || discount <= 0.0D) {
            return Math.max(0L, cost);
        }
        double scaled = cost * (1.0D - Math.clamp(discount, 0.0D, 0.90D));
        return Math.max(1L, (long) Math.ceil(scaled));
    }

    private ResearchCosts() {
    }
}
