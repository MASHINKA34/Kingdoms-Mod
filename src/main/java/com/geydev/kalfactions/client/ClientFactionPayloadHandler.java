package com.geydev.kalfactions.client;

import com.geydev.kalfactions.client.screen.ChestAccessScreen;
import com.geydev.kalfactions.client.screen.FactionListScreen;
import com.geydev.kalfactions.client.screen.FactionScreen;
import com.geydev.kalfactions.client.screen.FactionScreens;
import com.geydev.kalfactions.client.screen.GuideScreen;
import com.geydev.kalfactions.client.screen.NewsScreen;
import com.geydev.kalfactions.client.screen.SanctuaryMapScreen;
import com.geydev.kalfactions.client.screen.WarArchiveScreen;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.integration.xaero.XaeroMaps;
import com.geydev.kalfactions.net.FactionPayloads;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    public static void handleInviteBadge(FactionPayloads.S2CInviteBadge payload) {
        ClientInviteState.accept(payload.count());
    }

    public static void handleInfluenceGain(FactionPayloads.S2CInfluenceGain payload) {
        InfluenceType.parse(payload.influenceType()).ifPresent(type -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> InfluenceGainToast.show(type, payload.amount()));
        });
    }

    public static void handleMiningBonus(FactionPayloads.S2CMiningBonus payload) {
        ClientResearchBonuses.setMiningMultiplier(payload.multiplier());
    }

    public static void handleFactionList(FactionPayloads.S2CFactionList payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof FactionListScreen screen) {
                screen.acceptData(payload);
            }
        });
    }

    public static void handleWarArchive(FactionPayloads.S2CWarArchive payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof WarArchiveScreen screen) {
                screen.acceptData(payload.records());
            } else {
                minecraft.setScreen(new WarArchiveScreen(minecraft.screen, payload.records()));
            }
        });
    }

    public static void handleOpenGuide() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof GuideScreen) {
                return;
            }
            minecraft.setScreen(new GuideScreen(minecraft.screen));
        });
    }

    public static void handleOpenNews() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof NewsScreen) {
                return;
            }
            NewsScreen.open();
        });
    }

    public static void handleOpenSanctuary(FactionPayloads.S2COpenSanctuary payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof SanctuaryMapScreen screen
                    && screen.corePos().equals(payload.corePos())) {
                screen.acceptState(payload);
                return;
            }
            if (XaeroMaps.openWorldMap()) {
                return;
            }
            minecraft.setScreen(new SanctuaryMapScreen(payload));
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
        if (minecraft.player != null || minecraft.screen != null) {
            KingdomsNoticeToast.show(message, successful);
        }
    }

    public static void handleSyncClaims(FactionPayloads.S2CSyncClaims payload) {
        Map<Long, ClientClaimStore.ClaimInfo> claims = new HashMap<>();
        for (FactionPayloads.ClaimEntry entry : payload.claims()) {
            claims.put(
                    ChunkPos.asLong(entry.chunkX(), entry.chunkZ()),
                    new ClientClaimStore.ClaimInfo(
                            entry.color(),
                            entry.name(),
                            entry.factionId(),
                            entry.outpost(),
                            entry.forceLoaded(),
                            entry.sanctuary(),
                            entry.frozen())
            );
        }
        ClientClaimStore.ViewerInfo viewer = new ClientClaimStore.ViewerInfo(
                payload.viewerFactionId(),
                payload.viewerClaimCount(),
                payload.viewerClaimDiscount()
        );
        Set<UUID> memberIds = Set.copyOf(payload.viewerMemberIds());
        Minecraft.getInstance().execute(() -> {
            ClientFactionMembership.accept(payload.viewerFactionId(), memberIds);
            ClientClaimStore.replace(payload.dimension(), claims, viewer);
        });
    }

    private ClientFactionPayloadHandler() {
    }
}
