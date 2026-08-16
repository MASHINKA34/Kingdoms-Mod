package com.geydev.kalfactions;

import net.neoforged.fml.loading.FMLEnvironment;

public final class ClientBridge {
    private static volatile boolean scoutBusy;

    private ClientBridge() {
    }

    public static boolean scoutBusy() {
        return FMLEnvironment.dist.isClient() && scoutBusy;
    }

    public static void setScoutBusy(boolean value) {
        scoutBusy = value;
    }

    public static void openGuide() {
        if (FMLEnvironment.dist.isClient()) {
            ClientOnly.openGuide();
        }
    }

    public static void openNews() {
        if (FMLEnvironment.dist.isClient()) {
            ClientOnly.openNews();
        }
    }

    private static final class ClientOnly {
        private static void openGuide() {
            com.geydev.kalfactions.client.ClientFactionPayloadHandler.handleOpenGuide();
        }

        private static void openNews() {
            com.geydev.kalfactions.client.ClientFactionPayloadHandler.handleOpenNews();
        }
    }
}
