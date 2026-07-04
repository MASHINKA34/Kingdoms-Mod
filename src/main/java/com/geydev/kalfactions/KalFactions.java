package com.geydev.kalfactions;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModCreativeTabs;
import com.geydev.kalfactions.registry.ModEntities;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(KalFactions.MOD_ID)
public final class KalFactions {
    public static final String MOD_ID = "kingdoms";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KalFactions(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        if (FMLEnvironment.dist.isClient()) {
            ClientOnly.register(modBus);
        }
        container.registerConfig(ModConfig.Type.SERVER, ModConfigSpec.SPEC);
    }

    private static final class ClientOnly {
        private static void register(IEventBus modBus) {
            com.geydev.kalfactions.client.FactionListOpener.register(modBus);
            com.geydev.kalfactions.client.KingdomsClientRenderers.register(modBus);
            com.geydev.kalfactions.client.KingdomsMenuScreens.register(modBus);
        }
    }
}
