package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.time.Duration;

public record NetherRules(
        Duration sessionDuration,
        int sessionsPerDay,
        boolean requireFullSessionBeforeClose,
        int landingMinRadius,
        int landingMaxRadius,
        int landingAttempts,
        int landingMinimumSeparation
) {
    public static final NetherRules DEFAULT = new NetherRules(
            Duration.ofMinutes(90),
            2,
            true,
            1_000,
            5_000,
            8,
            512
    );

    public NetherRules {
        if (sessionDuration.isNegative() || sessionDuration.isZero()) {
            throw new IllegalArgumentException("sessionDuration");
        }
        if (sessionsPerDay < 1 || sessionsPerDay > 16) {
            throw new IllegalArgumentException("sessionsPerDay");
        }
        if (landingMinRadius < 0 || landingMaxRadius < landingMinRadius) {
            throw new IllegalArgumentException("landingRadius");
        }
        if (landingAttempts < 1 || landingAttempts > 64) {
            throw new IllegalArgumentException("landingAttempts");
        }
        if (landingMinimumSeparation < 0) {
            throw new IllegalArgumentException("landingMinimumSeparation");
        }
    }

    public static NetherRules configured() {
        int minRadius = ModConfigSpec.NETHER_LANDING_MIN_RADIUS.getAsInt();
        int maxRadius = Math.max(minRadius, ModConfigSpec.NETHER_LANDING_MAX_RADIUS.getAsInt());
        return new NetherRules(
                Duration.ofMinutes(ModConfigSpec.NETHER_SESSION_DURATION_MINUTES.getAsInt()),
                2,
                ModConfigSpec.NETHER_REQUIRE_FULL_SESSION_BEFORE_CLOSE.get(),
                minRadius,
                maxRadius,
                ModConfigSpec.NETHER_LANDING_ATTEMPTS.getAsInt(),
                ModConfigSpec.NETHER_LANDING_MINIMUM_SEPARATION.getAsInt()
        );
    }
}
