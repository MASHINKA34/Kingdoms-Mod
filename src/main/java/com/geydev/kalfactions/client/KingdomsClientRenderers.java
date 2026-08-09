package com.geydev.kalfactions.client;

import com.geydev.kalfactions.registry.ModBlockEntities;
import com.geydev.kalfactions.registry.ModEntities;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public final class KingdomsClientRenderers {
    public static void register(IEventBus modBus) {
        modBus.addListener(KingdomsClientRenderers::onRegisterRenderers);
        modBus.addListener(KingdomsClientRenderers::onRegisterClientExtensions);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OUTPOST_TRADER.get(), KingdomsTraderRenderer::new);
        event.registerEntityRenderer(ModEntities.SELLER_TRADER.get(), KingdomsSellerTraderRenderer::new);
        event.registerEntityRenderer(ModEntities.BANKER.get(), KingdomsBankerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.WORLD_MAP.get(), WorldMapRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.STATUE_SCIENCE.get(),
                StatueScienceCrystalRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.WAR_GOD_STATUE.get(),
                WarGodStatueRenderer::new
        );
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new WarGodStatueItemRenderer();
                }
                return renderer;
            }
        }, ModItems.WAR_GOD_STATUE.get());
    }

    private KingdomsClientRenderers() {
    }
}
