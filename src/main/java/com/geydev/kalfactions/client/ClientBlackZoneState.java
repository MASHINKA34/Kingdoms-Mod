package com.geydev.kalfactions.client;

import com.geydev.kalfactions.blackzone.BlackZoneStage;

public final class ClientBlackZoneState {
    private static volatile int stage = -1;
    private static volatile long accumulatedMillis;
    private static volatile long releaseMillis;
    private static volatile long receivedAt;
    private static volatile boolean inZone;

    public static void accept(int stageOrdinal, int accumulatedSeconds, boolean playerInZone, int releaseSeconds) {
        stage = stageOrdinal;
        accumulatedMillis = accumulatedSeconds * 1000L;
        releaseMillis = releaseSeconds * 1000L;
        receivedAt = System.currentTimeMillis();
        inZone = playerInZone;
    }

    public static void clear() {
        stage = -1;
        accumulatedMillis = 0L;
        releaseMillis = 0L;
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
        return accumulatedMillis + elapsed();
    }

    public static long releaseMillis() {
        if (!active() || inZone) {
            return 0L;
        }
        return Math.max(0L, releaseMillis - elapsed());
    }

    public static BlackZoneStage stage() {
        BlackZoneStage[] stages = BlackZoneStage.values();
        int index = stage;
        return index < 0 || index >= stages.length ? null : stages[index];
    }

    private static long elapsed() {
        return Math.max(0L, System.currentTimeMillis() - receivedAt);
    }

    private ClientBlackZoneState() {
    }
}
