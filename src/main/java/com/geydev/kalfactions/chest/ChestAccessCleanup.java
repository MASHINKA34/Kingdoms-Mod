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

/**
 * Keeps per-container access rules from outliving their container. A break is only a promise, so the
 * rule is dropped at the end of the tick and only once the block entity is really gone; a placement
 * has already happened, so a rule left behind at that position is stale and goes immediately.
 */
@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class ChestAccessCleanup {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        if (FactionManager.get(level).getChestAccess(ChestAccess.Key.of(level, pos)).isEmpty()) {
            return;
        }
        level.getServer().execute(() -> forgetIfContainerIsGone(level, pos));
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

    public static void forgetIfContainerIsGone(ServerLevel level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) == null) {
            forget(level, pos);
        }
    }

    private static void forget(ServerLevel level, BlockPos pos) {
        FactionManager.get(level).removeChestAccess(ChestAccess.Key.of(level, pos));
    }

    private ChestAccessCleanup() {
    }
}
