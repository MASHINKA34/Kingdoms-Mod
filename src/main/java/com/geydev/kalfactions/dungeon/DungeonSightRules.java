package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.config.ModConfigSpec;
import java.util.Arrays;

public record DungeonSightRules(boolean enabled, int[] percentByLevel, int effectTicks) {
    public static final DungeonSightRules DEFAULT =
            new DungeonSightRules(true, new int[] {35, 70, 100}, 300);

    private static volatile DungeonSightRules override;

    public DungeonSightRules {
        if (percentByLevel == null || percentByLevel.length != DungeonManager.MAX_LIGHTING) {
            throw new IllegalArgumentException("percentByLevel");
        }
        if (effectTicks < 1) {
            throw new IllegalArgumentException("effectTicks");
        }
        percentByLevel = Arrays.stream(percentByLevel)
                .map(percent -> Math.clamp(percent, DungeonSight.MIN_PERCENT, DungeonSight.MAX_PERCENT))
                .toArray();
    }

    public static DungeonSightRules configured() {
        DungeonSightRules forced = override;
        if (forced != null) {
            return forced;
        }
        try {
            return new DungeonSightRules(
                    ModConfigSpec.DUNGEON_SIGHT_ENABLED.get(),
                    new int[] {
                            ModConfigSpec.DUNGEON_SIGHT_LEVEL1_PERCENT.getAsInt(),
                            ModConfigSpec.DUNGEON_SIGHT_LEVEL2_PERCENT.getAsInt(),
                            ModConfigSpec.DUNGEON_SIGHT_LEVEL3_PERCENT.getAsInt()
                    },
                    ModConfigSpec.DUNGEON_SIGHT_EFFECT_TICKS.getAsInt()
            );
        } catch (IllegalStateException configNotLoaded) {
            return DEFAULT;
        }
    }

    public static void override(DungeonSightRules rules) {
        override = rules;
    }

    public static void reset() {
        override = null;
    }

    public int percentFor(int level) {
        if (level < 1 || level > percentByLevel.length) {
            return 0;
        }
        return percentByLevel[level - 1];
    }

    @Override
    public int[] percentByLevel() {
        return percentByLevel.clone();
    }
}
