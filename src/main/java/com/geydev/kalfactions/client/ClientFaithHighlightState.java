package com.geydev.kalfactions.client;

public final class ClientFaithHighlightState {
    private static volatile boolean enabled;
    private static volatile int radius = 12;
    private static volatile int maxBlocks = 256;
    private static volatile int scanTicks = 20;

    public static void accept(boolean newEnabled, int newRadius, int newMaxBlocks, int newScanTicks) {
        enabled = newEnabled;
        radius = Math.clamp(newRadius, 1, 64);
        maxBlocks = Math.clamp(newMaxBlocks, 1, 4096);
        scanTicks = Math.clamp(newScanTicks, 1, 600);
    }

    public static void clear() {
        enabled = false;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int radius() {
        return radius;
    }

    public static int maxBlocks() {
        return maxBlocks;
    }

    public static int scanTicks() {
        return scanTicks;
    }

    private ClientFaithHighlightState() {
    }
}
