package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SanctuaryBootstrapEvents {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        SanctuaryManager.get(level).initializeAutomaticSpawn(level);
    }

    private SanctuaryBootstrapEvents() {
    }
}
