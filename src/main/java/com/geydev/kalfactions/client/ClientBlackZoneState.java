package com.geydev.kalfactions.client;

import com.geydev.kalfactions.blackzone.BlackZoneStage;

public final class ClientBlackZoneState {
    private static volatile int stage = -1;
    private static volatile long accumulatedMillis;
    private static volatile long receivedAt;
    private static volatile boolean inZone;

    public static void accept(int stageOrdinal, int accumulatedSeconds, boolean playerInZone) {
        stage = stageOrdinal;
        accumulatedMillis = accumulatedSeconds * 1000L;
        receivedAt = System.currentTimeMillis();
        inZone = playerInZone;
    }

    public static void clear() {
        stage = -1;
        accumulatedMillis = 0L;
        receivedAt = 0L;
        inZone = false;
    }

    public static boolean active() {
        return stage >= 0;
    }

    public static boolean inZone() {
        return inZone;
    }

    public static long accumulatedMillis() {
        if (!active()) {
            return 0L;
        }
        if (!inZone) {
            return accumulatedMillis;
        }
        return accumulatedMillis + Math.max(0L, System.currentTimeMillis() - receivedAt);
    }

    public static BlackZoneStage stage() {
        BlackZoneStage[] stages = BlackZoneStage.values();
        int index = stage;
        return index < 0 || index >= stages.length ? null : stages[index];
    }

    private ClientBlackZoneState() {
    }
}
