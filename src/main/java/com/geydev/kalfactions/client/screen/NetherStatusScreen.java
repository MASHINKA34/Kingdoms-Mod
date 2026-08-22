package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.dimension.DimensionPayloads;
import com.geydev.kalfactions.dimension.NetherSchedulePolicy;
import java.time.Duration;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NetherStatusScreen extends Screen {
    private static final int PANEL_WIDTH = 286;
    private static final int PANEL_HEIGHT = 206;
    private DimensionPayloads.S2CNetherStatus state;
    private long receivedAtMillis;
    private int panelLeft;
    private int panelTop;
    private int refreshTicks;

    private NetherStatusScreen() {
        super(Component.translatable("screen.kingdoms.nether_status.title"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        minecraft.setScreen(new NetherStatusScreen());
        requestStatus();
    }

    public static void handle(DimensionPayloads.S2CNetherStatus payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof NetherStatusScreen screen) {
            screen.state = payload;
            screen.receivedAtMillis = Util.getMillis();
        }
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2,
                panelTop + PANEL_HEIGHT - 28,
                90,
                20
        ));
    }

    @Override
    public void tick() {
        if (++refreshTicks >= 100) {
            refreshTicks = 0;
            requestStatus();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xFFFFB15C);
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.kingdoms.nether_status.schedule"),
                width / 2,
                panelTop + 31,
                0xFFF4E4D2
        );
        if (state == null) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable("screen.kingdoms.nether_status.loading"),
                    width / 2,
                    panelTop + 68,
                    0xFFB8B0A4
            );
            return;
        }
        long now = state.serverNowEpochMillis() + Math.max(0L, Util.getMillis() - receivedAtMillis);
        long phaseSeconds = remainingSeconds(state.phaseEndsAtEpochMillis(), now);
        Component phase;
        int phaseColor;
        if (state.openForPlayers()) {
            phase = Component.translatable(
                    "screen.kingdoms.nether_status.closes_in", formatClock(phaseSeconds)
            );
            phaseColor = 0xFF8FD98F;
        } else if (state.administrativelyClosed()) {
            phase = Component.translatable(
                    "screen.kingdoms.nether_status.admin_closed", formatClock(phaseSeconds)
            );
            phaseColor = 0xFFE07A6B;
        } else {
            phase = Component.translatable(
                    "screen.kingdoms.nether_status.opens_in", formatClock(phaseSeconds)
            );
            phaseColor = 0xFFF3D58B;
        }
        graphics.drawCenteredString(font, phase, width / 2, panelTop + 53, phaseColor);
        if (state.remainingSessions() < 0) {
            renderWrappedCentered(
                    graphics,
                    Component.translatable("screen.kingdoms.nether_status.no_faction"),
                    panelTop + 76,
                    0xFFE07A6B
            );
        } else {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "screen.kingdoms.nether_status.sessions",
                            state.remainingSessions(),
                            state.totalSessions()
                    ),
                    width / 2,
                    panelTop + 76,
                    0xFFF4E4D2
            );
            if (state.sessionEndsAtEpochMillis() > now) {
                graphics.drawCenteredString(
                        font,
                        Component.translatable(
                                "screen.kingdoms.nether_status.current_session",
                                formatClock(remainingSeconds(state.sessionEndsAtEpochMillis(), now))
                        ),
                        width / 2,
                        panelTop + 96,
                        0xFFCC9AF2
                );
            }
        }
        renderPortalLine(graphics, now);
    }

    private void renderPortalLine(GuiGraphics graphics, long now) {
        graphics.fill(panelLeft + 24, panelTop + 112, panelLeft + PANEL_WIDTH - 24, panelTop + 113, 0xFF6B4E2A);
        if (state.portalChargedUntilEpochMillis() > now) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(
                            "screen.kingdoms.nether_status.portal_closes_in",
                            NetherSchedulePolicy.formatRemaining(Duration.ofSeconds(
                                    remainingSeconds(state.portalChargedUntilEpochMillis(), now)
                            ))
                    ),
                    width / 2,
                    panelTop + 122,
                    0xFFFFB15C
            );
            return;
        }
        graphics.drawCenteredString(
                font,
                Component.translatable("screen.kingdoms.nether_status.portal_unlit"),
                width / 2,
                panelTop + 122,
                0xFFE07A6B
        );
        renderWrappedCentered(
                graphics,
                Component.translatable("screen.kingdoms.nether_status.portal_unlit_hint"),
                panelTop + 140,
                0xFFB8B0A4
        );
    }

    private void renderWrappedCentered(GuiGraphics graphics, Component text, int y, int color) {
        for (FormattedCharSequence line : font.split(text, PANEL_WIDTH - 28)) {
            graphics.drawCenteredString(font, line, width / 2, y, color);
            y += font.lineHeight + 2;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFFC99645);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xF02A211D);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void requestStatus() {
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(DimensionPayloads.C2SNetherStatusRequest.INSTANCE);
        }
    }

    private static long remainingSeconds(long endEpochMillis, long nowEpochMillis) {
        return Math.max(0L, (endEpochMillis - nowEpochMillis + 999L) / 1000L);
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
}
