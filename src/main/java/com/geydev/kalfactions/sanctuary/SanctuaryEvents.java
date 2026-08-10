package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SanctuaryEvents {
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!ModConfigSpec.SANCTUARY_DISABLE_MOB_SPAWNS.get()
                || !(event.getLevel().getLevel() instanceof ServerLevel level)
                || !blocksSpawnType(event.getSpawnType())) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (SanctuaryManager.get(level).isSanctuary(ClaimKey.of(level, pos))) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!ModConfigSpec.SANCTUARY_DISABLE_LIGHTNING.get()
                || !(event.getEntity() instanceof LightningBolt bolt)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (SanctuaryManager.get(level).isSanctuary(ClaimKey.of(level, bolt.blockPosition()))) {
            event.setCanceled(true);
        }
    }

    private static boolean blocksSpawnType(MobSpawnType type) {
        return switch (type) {
            case SPAWN_EGG, COMMAND, BUCKET, BREEDING, MOB_SUMMONED, CONVERSION, DISPENSER, TRIGGERED -> false;
            default -> true;
        };
    }

    private SanctuaryEvents() {
    }
}
