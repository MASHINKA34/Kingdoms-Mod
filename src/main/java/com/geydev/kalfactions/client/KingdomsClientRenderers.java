package com.geydev.kalfactions.client;

import com.geydev.kalfactions.registry.ModBlockEntities;
import com.geydev.kalfactions.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class KingdomsClientRenderers {
    public static void register(IEventBus modBus) {
        modBus.addListener(KingdomsClientRenderers::onRegisterRenderers);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OUTPOST_TRADER.get(), KingdomsTraderRenderer::new);
        event.registerEntityRenderer(ModEntities.SELLER_TRADER.get(), KingdomsSellerTraderRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GUIDE_BOARD.get(), GuideBoardRenderer::new);
    }

    private KingdomsClientRenderers() {
    }
}
