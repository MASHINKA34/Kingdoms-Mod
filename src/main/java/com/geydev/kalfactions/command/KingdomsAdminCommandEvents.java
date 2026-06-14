package com.geydev.kalfactions.command;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class KingdomsAdminCommandEvents {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        KingdomsAdminCommands.register(event.getDispatcher());
    }

    private KingdomsAdminCommandEvents() {
    }
}
