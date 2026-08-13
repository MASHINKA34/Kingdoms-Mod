package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DungeonEvents {
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!ModConfigSpec.DUNGEON_DISABLE_MOB_SPAWNS.get()
                || !(event.getLevel().getLevel() instanceof ServerLevel level)
                || !blocksSpawnType(event.getSpawnType())) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (DungeonProtection.isDungeon(level, pos)) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean blocksSpawnType(MobSpawnType type) {
        if (type == MobSpawnType.SPAWNER) {
            return !ModConfigSpec.DUNGEON_ALLOW_SPAWNER_MOBS.get();
        }
        return switch (type) {
            case SPAWN_EGG, COMMAND, BUCKET, BREEDING, MOB_SUMMONED, CONVERSION, DISPENSER, TRIGGERED -> false;
            default -> true;
        };
    }

    private DungeonEvents() {
    }
}
