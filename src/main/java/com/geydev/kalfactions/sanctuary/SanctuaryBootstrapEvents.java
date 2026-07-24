package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SanctuaryBootstrapEvents {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos anchor = manager.initializeAutomaticSpawn(level);
        if (anchor != null && !level.getBlockState(anchor).is(ModBlocks.SANCTUARY_CORE.get())) {
            level.setBlockAndUpdate(anchor, ModBlocks.SANCTUARY_CORE.get().defaultBlockState());
        }
    }

    private SanctuaryBootstrapEvents() {
    }
}
