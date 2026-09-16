package com.geydev.kalfactions.item;

import com.geydev.kalfactions.config.ModConfigSpec;

public record WarpScrollRules(
        int castTicks,
        boolean cancelOnMove,
        double moveToleranceSquared,
        boolean cancelOnDamage,
        int damageCooldownTicks,
        int cancelCooldownTicks
) {
    public static final WarpScrollRules DEFAULT = of(4, true, 0.2D, true, 3, 0);
    public static final WarpScrollRules INSTANT = of(0, true, 0.2D, true, 3, 0);

    private static volatile WarpScrollRules override;

    public WarpScrollRules {
        if (castTicks < 0) {
            throw new IllegalArgumentException("castTicks");
        }
        if (moveToleranceSquared < 0.0D) {
            throw new IllegalArgumentException("moveToleranceSquared");
        }
        if (damageCooldownTicks < 0) {
            throw new IllegalArgumentException("damageCooldownTicks");
        }
        if (cancelCooldownTicks < 0) {
            throw new IllegalArgumentException("cancelCooldownTicks");
        }
    }

    public static WarpScrollRules of(
            int castSeconds,
            boolean cancelOnMove,
            double moveToleranceBlocks,
            boolean cancelOnDamage,
            int damageCooldownSeconds,
            int cancelCooldownSeconds
    ) {
        double tolerance = Math.max(0.0D, moveToleranceBlocks);
        return new WarpScrollRules(
                Math.max(0, castSeconds) * 20,
                cancelOnMove,
                tolerance * tolerance,
                cancelOnDamage,
                Math.max(0, damageCooldownSeconds) * 20,
                Math.max(0, cancelCooldownSeconds) * 20
        );
    }

    public static WarpScrollRules configured() {
        WarpScrollRules forced = override;
        if (forced != null) {
            return forced;
        }
        try {
            return of(
                    ModConfigSpec.WARP_SCROLL_CAST_SECONDS.getAsInt(),
                    ModConfigSpec.WARP_SCROLL_CANCEL_ON_MOVE.get(),
                    ModConfigSpec.WARP_SCROLL_MOVE_TOLERANCE_BLOCKS.getAsDouble(),
                    ModConfigSpec.WARP_SCROLL_CANCEL_ON_DAMAGE.get(),
                    ModConfigSpec.WARP_SCROLL_DAMAGE_COOLDOWN_SECONDS.getAsInt(),
                    ModConfigSpec.WARP_SCROLL_CANCEL_COOLDOWN_SECONDS.getAsInt()
            );
        } catch (IllegalStateException configNotLoaded) {
            return DEFAULT;
        }
    }

    public static void override(WarpScrollRules rules) {
        override = rules;
    }

    public static void reset() {
        override = null;
    }

    public int castSeconds() {
        return (castTicks + 19) / 20;
    }

    public int damageCooldownSeconds() {
        return (damageCooldownTicks + 19) / 20;
    }
}
