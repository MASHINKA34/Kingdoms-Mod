package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.dimension.DimensionPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

public final class NetherHudOverlay {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "nether_status");
    private static DimensionPayloads.S2CNetherHud state;
    private static long receivedAtMillis;

    public static void register(IEventBus modBus) {
        modBus.addListener(NetherHudOverlay::registerLayer);
    }

    public static void handle(DimensionPayloads.S2CNetherHud payload) {
        state = payload;
        receivedAtMillis = Util.getMillis();
    }

    private static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, NetherHudOverlay::render);
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        DimensionPayloads.S2CNetherHud current = state;
        if (current == null || !current.visible() || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        long estimatedServerNow = current.serverNowEpochMillis() + Math.max(0L, Util.getMillis() - receivedAtMillis);
        long phaseSeconds = remainingSeconds(current.phaseEndsAtEpochMillis(), estimatedServerNow);
        if (phaseSeconds <= 0L && (current.preview() || !current.opening())) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("hud.kingdoms.nether.title"));
        if (current.opening()) {
            lines.add(Component.translatable("hud.kingdoms.nether.opening"));
            lines.add(Component.literal(formatOpening(phaseSeconds)));
        } else {
            lines.add(Component.translatable("hud.kingdoms.nether.open"));
            lines.add(Component.translatable(
                    "hud.kingdoms.nether.until_close", formatClock(phaseSeconds)
            ));
            if (current.remainingSessions() < 0) {
                lines.add(Component.translatable("hud.kingdoms.nether.no_faction"));
            } else {
                lines.add(Component.translatable(
                        "hud.kingdoms.nether.sessions",
                        current.remainingSessions(),
                        current.totalSessions()
                ));
                if (current.sessionEndsAtEpochMillis() > estimatedServerNow) {
                    lines.add(Component.translatable(
                            "hud.kingdoms.nether.current_session",
                            formatClock(remainingSeconds(current.sessionEndsAtEpochMillis(), estimatedServerNow))
                    ));
                }
            }
        }
        Font font = minecraft.font;
        int screenWidth = graphics.guiWidth();
        int maxPanelWidth = Math.max(96, Math.min(220, screenWidth - 12));
        int desiredWidth = 148;
        for (Component line : lines) {
            desiredWidth = Math.max(desiredWidth, font.width(line) + 16);
        }
        int panelWidth = Math.min(maxPanelWidth, desiredWidth);
        int textWidth = Math.max(80, panelWidth - 16);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(font.split(line, textWidth));
        }
        int lineHeight = font.lineHeight + 2;
        int panelHeight = 12 + wrapped.size() * lineHeight;
        int left = screenWidth - panelWidth - 6;
        int top = Math.max(6, graphics.guiHeight() / 5);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, 0xCC9E6B35);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xCC241C1A);
        int y = top + 6;
        for (int index = 0; index < wrapped.size(); index++) {
            int color = index == 0 ? 0xFFFFB15C : 0xFFF4E4D2;
            graphics.drawString(font, wrapped.get(index), left + 8, y, color, true);
            y += lineHeight;
        }
    }

    private static long remainingSeconds(long endEpochMillis, long nowEpochMillis) {
        return Math.max(0L, (endEpochMillis - nowEpochMillis + 999L) / 1000L);
    }

    private static String formatOpening(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%d:%02d", safe / 60L, safe % 60L);
    }

    private static String formatClock(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(
                Locale.ROOT,
                "%02d:%02d:%02d",
                safe / 3600L,
                (safe % 3600L) / 60L,
                safe % 60L
        );
    }

    private NetherHudOverlay() {
    }
}
