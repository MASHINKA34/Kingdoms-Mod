package com.geydev.kalfactions.science;

public enum ResearchBenchStatus {
    WORKING,
    NO_MATERIALS,
    DAILY_CAP,
    OFF_TERRITORY;

    private static final ResearchBenchStatus[] VALUES = values();

    public static ResearchBenchStatus byOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? NO_MATERIALS : VALUES[ordinal];
    }

    public boolean running() {
        return this == WORKING;
    }
}
