package com.geydev.kalfactions.protection;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dungeon.DungeonProtection;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.sanctuary.SanctuaryFire;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

public final class FireProtection {
    public static boolean blocksFire(LevelReader level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel
                && (SanctuaryFire.blocksFire(serverLevel, pos)
                    || DungeonProtection.blocksFire(serverLevel, pos)
                    || ModConfigSpec.PROTECT_FIRE.get()
                        && FactionManager.get(serverLevel).getFactionIdAt(ClaimKey.of(serverLevel, pos)).isPresent());
    }

    private FireProtection() {
    }
}
