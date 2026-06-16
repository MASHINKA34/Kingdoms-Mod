package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class KingdomsClientRenderers {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.OUTPOST_TRADER.get(), KingdomsTraderRenderer::new);
        event.registerEntityRenderer(ModEntities.SELLER_TRADER.get(), KingdomsSellerTraderRenderer::new);
    }

    private KingdomsClientRenderers() {
    }
}
