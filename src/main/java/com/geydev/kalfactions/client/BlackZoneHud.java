package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.blackzone.BlackZoneFormat;
import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class BlackZoneHud {
    private static final String MINIMAP_MOD_ID = "xaerominimap";
    private static final int DEFAULT_INTERVAL_SECONDS = 30;

    private static long lastShownAt;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            ClientBlackZoneState.clear();
            lastShownAt = 0L;
            return;
        }
        if (!ClientBlackZoneState.active() || ModList.get().isLoaded(MINIMAP_MOD_ID)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastShownAt < intervalMillis()) {
            return;
        }
        lastShownAt = now;
        minecraft.player.displayClientMessage(
                Component.translatable(
                        "kingdoms.blackzone.display.timer",
                        BlackZoneFormat.clock(ClientBlackZoneState.accumulatedMillis())
                ),
                true
        );
    }

    private static long intervalMillis() {
        try {
            return ModConfigSpec.BLACK_ZONE_ACTIONBAR_INTERVAL_SECONDS.getAsInt() * 1000L;
        } catch (IllegalStateException configNotLoaded) {
            return DEFAULT_INTERVAL_SECONDS * 1000L;
        }
    }

    private BlackZoneHud() {
    }
}
