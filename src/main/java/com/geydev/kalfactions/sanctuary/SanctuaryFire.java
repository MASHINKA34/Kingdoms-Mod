package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

public final class SanctuaryFire {
    public static boolean blocksFire(LevelReader level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && blocksFire(serverLevel, pos);
    }

    public static boolean blocksFire(ServerLevel level, BlockPos pos) {
        return ModConfigSpec.SANCTUARY_DISABLE_FIRE_SPREAD.get()
                && SanctuaryManager.get(level).isSanctuary(level, pos);
    }

    private SanctuaryFire() {
    }
}
