package com.geydev.kalfactions.integration.xaero;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;

final class XaeroWorldMapScreens {
    static boolean showNotice(net.minecraft.network.chat.Component message, boolean successful) {
        if (Minecraft.getInstance().screen instanceof KingdomsGuiMap map) {
            map.showNotice(message, successful);
            return true;
        }
        return false;
    }

    static boolean open() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null) {
            return false;
        }
        MapProcessor processor = session.getMapProcessor();
        if (processor == null) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity() == null ? minecraft.player : minecraft.getCameraEntity();
        if (camera == null) {
            return false;
        }
        minecraft.setScreen(new KingdomsGuiMap(null, null, processor, camera));
        return true;
    }

    private XaeroWorldMapScreens() {
    }
}
