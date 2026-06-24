package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.DrillScreen;
import com.geydev.kalfactions.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class KingdomsMenuScreens {
    public static void register(IEventBus modBus) {
        modBus.addListener(KingdomsMenuScreens::onRegisterMenuScreens);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.DRILL.get(), DrillScreen::new);
    }

    private KingdomsMenuScreens() {
    }
}
