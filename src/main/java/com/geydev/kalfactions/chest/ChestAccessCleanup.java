package com.geydev.kalfactions.chest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.FactionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ChestAccessCleanup {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            forget(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                forget(level, snapshot.getPos());
            }
            return;
        }
        forget(level, event.getPos());
    }

    private static void forget(ServerLevel level, BlockPos pos) {
        FactionManager.get(level).removeChestAccess(ChestAccess.Key.of(level, pos));
    }

    private ChestAccessCleanup() {
    }
}
