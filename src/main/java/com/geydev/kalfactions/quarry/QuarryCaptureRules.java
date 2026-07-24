package com.geydev.kalfactions.quarry;

public final class QuarryCaptureRules {
    public static TickResult tick(
            boolean attackersPresent,
            boolean defendersPresent,
            int remainingTicks,
            int elapsedTicks,
            int fullDurationTicks
    ) {
        int duration = Math.max(1, fullDurationTicks);
        int remaining = Math.clamp(remainingTicks, 0, duration);
        if (!attackersPresent) {
            return new TickResult(Action.RESET, duration);
        }
        if (defendersPresent) {
            return new TickResult(Action.PAUSED, remaining);
        }
        int updated = Math.max(0, remaining - Math.max(0, elapsedTicks));
        return new TickResult(updated == 0 ? Action.CAPTURED : Action.COUNTING, updated);
    }

    public enum Action {
        RESET,
        PAUSED,
        COUNTING,
        CAPTURED
    }

    public record TickResult(Action action, int remainingTicks) {
    }

    private QuarryCaptureRules() {
    }
}
