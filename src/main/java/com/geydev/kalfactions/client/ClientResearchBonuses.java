package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class ClientResearchBonuses {
    private static volatile float miningMultiplier = 1.0F;

    public static void setMiningMultiplier(float multiplier) {
        miningMultiplier = Float.isFinite(multiplier) && multiplier > 0.0F ? multiplier : 1.0F;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof LocalPlayer && miningMultiplier != 1.0F) {
            event.setNewSpeed(event.getNewSpeed() * miningMultiplier);
        }
    }

    private ClientResearchBonuses() {
    }
}
