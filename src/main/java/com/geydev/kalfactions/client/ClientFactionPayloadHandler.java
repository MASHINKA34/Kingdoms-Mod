package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.FactionScreen;
import com.geydev.kalfactions.client.screen.FactionScreens;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;

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

    public static void handleSyncClaims(FactionPayloads.S2CSyncClaims payload) {
        Map<Long, ClientClaimStore.ClaimInfo> claims = new HashMap<>();
        for (FactionPayloads.ClaimEntry entry : payload.claims()) {
            claims.put(
                    ChunkPos.asLong(entry.chunkX(), entry.chunkZ()),
                    new ClientClaimStore.ClaimInfo(entry.color(), entry.name(), entry.factionId())
            );
        }
        Minecraft.getInstance().execute(() -> ClientClaimStore.replace(payload.dimension(), claims));
    }

    private ClientFactionPayloadHandler() {
    }
}
