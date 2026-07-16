package com.geydev.kalfactions;

import net.neoforged.fml.loading.FMLEnvironment;

public final class ClientBridge {
    private static final String PAYLOAD_HANDLER = "com.geydev.kalfactions.client.ClientFactionPayloadHandler";

    private ClientBridge() {
    }

    public static void openGuide() {
        invoke("handleOpenGuide");
    }

    public static void openNews() {
        invoke("handleOpenNews");
    }

    private static void invoke(String method) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        try {
            Class.forName(PAYLOAD_HANDLER).getMethod(method).invoke(null);
        } catch (ReflectiveOperationException exception) {
            KalFactions.LOGGER.error("Failed to run client hook {}", method, exception);
        }
    }
}
