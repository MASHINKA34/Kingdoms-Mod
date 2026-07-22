package com.geydev.kalfactions.outpost.cluster.distribution;

public final class FiniteResourceLedger {
    public static Extraction extract(int remaining, int requested) {
        if (remaining < 0) {
            throw new IllegalArgumentException("remaining must not be negative");
        }
        if (requested < 0) {
            throw new IllegalArgumentException("requested must not be negative");
        }
        int extracted = Math.min(remaining, requested);
        return new Extraction(extracted, remaining - extracted);
    }

    public record Extraction(int extracted, int remaining) {
    }

    private FiniteResourceLedger() {
    }
}
