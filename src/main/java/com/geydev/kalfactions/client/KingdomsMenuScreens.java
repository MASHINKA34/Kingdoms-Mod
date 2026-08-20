package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.DrillScreen;
import com.geydev.kalfactions.client.screen.DungeonLootScreen;
import com.geydev.kalfactions.client.screen.KeyForgeScreen;
import com.geydev.kalfactions.client.screen.QuarryScreen;
import com.geydev.kalfactions.client.screen.ResearchBenchScreen;
import com.geydev.kalfactions.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public final class KingdomsMenuScreens {
    public static void register(IEventBus modBus) {
        modBus.addListener(KingdomsMenuScreens::onRegisterMenuScreens);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.DRILL.get(), DrillScreen::new);
        event.register(ModMenuTypes.QUARRY.get(), QuarryScreen::new);
        event.register(ModMenuTypes.DUNGEON_LOOT.get(), DungeonLootScreen::new);
        event.register(ModMenuTypes.RESEARCH_BENCH.get(), ResearchBenchScreen::new);
        event.register(ModMenuTypes.GHOST_KEY_FORGE.get(), KeyForgeScreen::new);
        event.register(ModMenuTypes.SCULK_KEY_FORGE.get(), KeyForgeScreen::new);
        event.register(ModMenuTypes.INFERNAL_KEY_FORGE.get(), KeyForgeScreen::new);
    }

    private KingdomsMenuScreens() {
    }
}
