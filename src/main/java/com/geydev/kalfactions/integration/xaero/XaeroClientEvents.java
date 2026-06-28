package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.client.ClientWorldMapStore;
import com.geydev.kalfactions.client.ClientWorldMapTracks;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID, value = Dist.CLIENT)
public final class XaeroClientEvents {
    private static final String MINIMAP_MOD_ID = "xaerominimap";
    private static final String WORLD_MAP_MOD_ID = "xaeroworldmap";

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            ClientClaimStore.clear();
            ClientWorldMapStore.clear();
            ClientWorldMapTracks.clear();
            return;
        }
        if (ModList.get().isLoaded(MINIMAP_MOD_ID)) {
            XaeroIntegration.tick();
        }
        if (ModList.get().isLoaded(WORLD_MAP_MOD_ID)) {
            XaeroWorldMapIntegration.tick();
        }
    }

    private XaeroClientEvents() {
    }
}
