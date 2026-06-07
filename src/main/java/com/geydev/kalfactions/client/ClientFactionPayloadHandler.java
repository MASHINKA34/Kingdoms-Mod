package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.FactionScreen;
import com.geydev.kalfactions.client.screen.FactionScreens;
import com.geydev.kalfactions.net.FactionPayloads;
import net.minecraft.client.Minecraft;

public final class ClientFactionPayloadHandler {
    public static void handle(FactionPayloads.S2CFactionState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ClientFactionCache.accept(payload.snapshot(), payload.successful(), payload.message());
            if (!payload.openScreen()) {
                return;
            }

            if (minecraft.screen instanceof FactionScreen screen
                    && screen.tablePos().equals(payload.snapshot().tablePos())) {
                screen.acceptServerState(payload.snapshot(), payload.successful(), payload.message());
            } else {
                FactionScreens.openRoot(payload.snapshot(), payload.successful(), payload.message());
            }
        });
    }

    private ClientFactionPayloadHandler() {
    }
}
