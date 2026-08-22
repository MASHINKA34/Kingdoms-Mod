package com.geydev.kalfactions.dimension;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.time.Duration;

public record NetherRules(
        Duration sessionDuration,
        int sessionsPerDay,
        int landingMinRadius,
        int landingMaxRadius,
        int landingAttempts,
        int landingMinimumSeparation,
        Duration portalLifetime
) {
    public static final NetherRules DEFAULT = new NetherRules(
            Duration.ofMinutes(90),
            2,
            1_000,
            5_000,
            8,
            512,
            Duration.ofHours(48)
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
        if (portalLifetime.isNegative() || portalLifetime.isZero()) {
            throw new IllegalArgumentException("portalLifetime");
        }
    }

    public static NetherRules configured() {
        int minRadius = ModConfigSpec.NETHER_LANDING_MIN_RADIUS.getAsInt();
        int maxRadius = Math.max(minRadius, ModConfigSpec.NETHER_LANDING_MAX_RADIUS.getAsInt());
        return new NetherRules(
                Duration.ofMinutes(ModConfigSpec.NETHER_SESSION_DURATION_MINUTES.getAsInt()),
                2,
                minRadius,
                maxRadius,
                ModConfigSpec.NETHER_LANDING_ATTEMPTS.getAsInt(),
                ModConfigSpec.NETHER_LANDING_MINIMUM_SEPARATION.getAsInt(),
                Duration.ofSeconds(Math.max(
                        1L,
                        Math.round(ModConfigSpec.NETHER_PORTAL_LIFETIME_HOURS.getAsDouble() * 3600.0D)
                ))
        );
    }
}
