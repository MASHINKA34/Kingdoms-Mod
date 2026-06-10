package com.geydev.kalfactions.integration.xaero;

import com.geydev.kalfactions.KalFactions;
import net.neoforged.fml.ModList;

public final class XaeroMaps {
    private static final String WORLD_MAP_MOD_ID = "xaeroworldmap";
    private static boolean failureLogged;

    public static boolean openWorldMap() {
        if (!ModList.get().isLoaded(WORLD_MAP_MOD_ID)) {
            return false;
        }
        try {
            return XaeroWorldMapScreens.open();
        } catch (RuntimeException | LinkageError exception) {
            if (!failureLogged) {
                failureLogged = true;
                KalFactions.LOGGER.warn("Could not open the Xaero world map", exception);
            }
            return false;
        }
    }

    private XaeroMaps() {
    }
}
