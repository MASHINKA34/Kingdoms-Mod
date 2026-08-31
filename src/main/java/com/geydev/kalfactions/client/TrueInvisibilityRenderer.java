package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class TrueInvisibilityRenderer {
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (ClientTrueInvisibility.isHidden(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private TrueInvisibilityRenderer() {
    }
}
