package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SanctuaryEvents {
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!ModConfigSpec.SANCTUARY_DISABLE_MOB_SPAWNS.get()
                || !(event.getLevel().getLevel() instanceof ServerLevel level)
                || (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof Enemy))) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (SanctuaryManager.get(level).isSanctuary(ClaimKey.of(level, pos))) {
            event.setSpawnCancelled(true);
        }
    }

    private SanctuaryEvents() {
    }
}
