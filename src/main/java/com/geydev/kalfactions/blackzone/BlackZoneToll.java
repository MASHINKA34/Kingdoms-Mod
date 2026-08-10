package com.geydev.kalfactions.blackzone;

public record BlackZoneToll(long accumulatedMillis, long lastInZoneAt, int notifiedStage, long lastKolyvanAt) {
    public static final BlackZoneToll EMPTY = new BlackZoneToll(0L, 0L, -1, 0L);

    public BlackZoneToll {
        accumulatedMillis = Math.max(0L, accumulatedMillis);
        lastInZoneAt = Math.max(0L, lastInZoneAt);
        notifiedStage = Math.max(-1, notifiedStage);
        lastKolyvanAt = Math.max(0L, lastKolyvanAt);
    }

    public boolean isEmpty() {
        return accumulatedMillis <= 0L;
    }

    public BlackZoneToll advance(long deltaMillis, long now) {
        return new BlackZoneToll(accumulatedMillis + Math.max(0L, deltaMillis), now, notifiedStage, lastKolyvanAt);
    }

    public BlackZoneToll withAccumulated(long millis, long now) {
        return new BlackZoneToll(millis, now, notifiedStage, lastKolyvanAt);
    }

    public BlackZoneToll withNotifiedStage(int stage) {
        return new BlackZoneToll(accumulatedMillis, lastInZoneAt, stage, lastKolyvanAt);
    }

    public BlackZoneToll withKolyvanAt(long now) {
        return new BlackZoneToll(accumulatedMillis, lastInZoneAt, notifiedStage, now);
    }
}
