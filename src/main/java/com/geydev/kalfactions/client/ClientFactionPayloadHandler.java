package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.ChestAccessScreen;
import com.geydev.kalfactions.client.screen.FactionListScreen;
import com.geydev.kalfactions.client.screen.FactionScreen;
import com.geydev.kalfactions.client.screen.FactionScreens;
import com.geydev.kalfactions.integration.xaero.XaeroMaps;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

public final class ClientFactionPayloadHandler {
    public static void handle(FactionPayloads.S2CFactionState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            String message = payload.message().getString();
            ClientFactionCache.accept(payload.snapshot(), payload.successful(), message);
            if (!payload.openScreen()) {
                if (!message.isBlank()) {
                    showNotice(minecraft, payload.message(), payload.successful());
                }
                return;
            }

            if (minecraft.screen instanceof FactionScreen screen
                    && screen.tablePos().equals(payload.snapshot().tablePos())) {
                screen.acceptServerState(payload.snapshot(), payload.successful(), message);
            } else {
                FactionScreens.openRoot(payload.snapshot(), payload.successful(), message);
            }
        });
    }

    public static void handleNotice(FactionPayloads.S2CFactionNotice payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> showNotice(minecraft, payload.message(), payload.successful()));
    }

    public static void handleFactionList(FactionPayloads.S2CFactionList payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof FactionListScreen screen) {
                screen.acceptData(payload);
            }
        });
    }

    public static void handleChestAccess(FactionPayloads.S2CChestAccessState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof ChestAccessScreen screen && screen.pos().equals(payload.pos())) {
                screen.acceptState(payload);
            } else {
                minecraft.setScreen(new ChestAccessScreen(payload));
            }
        });
    }

    private static void showNotice(Minecraft minecraft, Component message, boolean successful) {
        if (XaeroMaps.showMapNotice(message, successful)) {
            return;
        }
        if (minecraft.screen instanceof FactionListScreen listScreen) {
            listScreen.showNotice(message, successful);
            return;
        }
        if (minecraft.screen instanceof FactionScreen screen) {
            screen.acceptStatus(message.getString(), successful);
            return;
        }
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, true);
        }
    }

    public static void handleSyncClaims(FactionPayloads.S2CSyncClaims payload) {
        Map<Long, ClientClaimStore.ClaimInfo> claims = new HashMap<>();
        for (FactionPayloads.ClaimEntry entry : payload.claims()) {
            claims.put(
                    ChunkPos.asLong(entry.chunkX(), entry.chunkZ()),
                    new ClientClaimStore.ClaimInfo(
                            entry.color(), entry.name(), entry.factionId(), entry.outpost(), entry.forceLoaded())
            );
        }
        ClientClaimStore.ViewerInfo viewer = new ClientClaimStore.ViewerInfo(
                payload.viewerFactionId(),
                payload.viewerClaimCount(),
                payload.viewerClaimDiscount()
        );
        Minecraft.getInstance().execute(() -> ClientClaimStore.replace(payload.dimension(), claims, viewer));
    }

    private ClientFactionPayloadHandler() {
    }
}
