package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.dimension.DimensionPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DimensionControlScreen extends Screen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 220;
    private static final int SECTION_NETHER_TOP = 26;
    private static final int SECTION_END_TOP = 108;

    private DimensionPayloads.S2CDimensionState state;
    private boolean netherWipeArmed;
    private boolean endWipeArmed;
    private int panelLeft;
    private int panelTop;

    private DimensionControlScreen(DimensionPayloads.S2CDimensionState state) {
        super(Component.translatable("screen.kingdoms.dimension_title"));
        this.state = state;
    }

    public static void handle(DimensionPayloads.S2CDimensionState payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DimensionControlScreen screen) {
            screen.update(payload);
        } else {
            minecraft.setScreen(new DimensionControlScreen(payload));
        }
    }

    private void update(DimensionPayloads.S2CDimensionState payload) {
        state = payload;
        netherWipeArmed = false;
        endWipeArmed = false;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        addSection(false, SECTION_NETHER_TOP, state.netherClosed(), state.netherWipePending());
        addSection(true, SECTION_END_TOP, state.endClosed(), state.endWipePending());

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2,
                panelTop + PANEL_HEIGHT - 26,
                90,
                20
        ));
    }

    private void addSection(boolean end, int sectionTop, boolean closed, boolean wipePending) {
        int buttonsY = panelTop + sectionTop + 26;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable(closed ? "screen.kingdoms.dimension.open" : "screen.kingdoms.dimension.close"),
                button -> send(end, closed ? DimensionPayloads.ACTION_OPEN : DimensionPayloads.ACTION_CLOSE),
                panelLeft + 8,
                buttonsY,
                119,
                20
        ));
        if (wipePending) {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.dimension.wipe_cancel"),
                    button -> send(end, DimensionPayloads.ACTION_WIPE_CANCEL),
                    panelLeft + 133,
                    buttonsY,
                    119,
                    20
            ));
        } else {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.dimension.wipe"),
                    button -> {
                        boolean armed = end ? endWipeArmed : netherWipeArmed;
                        if (!armed) {
                            if (end) {
                                endWipeArmed = true;
                            } else {
                                netherWipeArmed = true;
                            }
                            button.setMessage(Component.translatable("screen.kingdoms.dimension.wipe_confirm"));
                            return;
                        }
                        send(end, DimensionPayloads.ACTION_WIPE_SCHEDULE);
                    },
                    panelLeft + 133,
                    buttonsY,
                    119,
                    20
            ));
        }
    }

    private static void send(boolean end, int action) {
        PacketDistributor.sendToServer(new DimensionPayloads.C2SDimensionAction(end, action));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);
        renderSection(graphics, false, SECTION_NETHER_TOP,
                state.netherClosed(), state.netherWipePending(), state.netherPlayers());
        renderSection(graphics, true, SECTION_END_TOP,
                state.endClosed(), state.endWipePending(), state.endPlayers());
        if (!state.notice().getString().isBlank()) {
            Component notice = state.notice();
            int color = state.successful() ? 0xFF8FD98F : 0xFFE07A6B;
            int noticeWidth = Math.min(font.width(notice), PANEL_WIDTH - 16);
            graphics.drawString(font, notice,
                    panelLeft + (PANEL_WIDTH - noticeWidth) / 2, panelTop + 176, color, true);
        }
        Component schedule = state.netherScheduleOpen()
                ? Component.translatable(
                        "screen.kingdoms.dimension.nether_schedule_open",
                        formatDuration(state.netherSecondsUntilClose()),
                        state.netherActiveSessions()
                )
                : Component.translatable("screen.kingdoms.dimension.nether_schedule_closed");
        graphics.drawString(font, schedule, panelLeft + 8, panelTop + SECTION_NETHER_TOP + 50,
                state.netherScheduleOpen() ? 0xFF8FD98F : 0xFFE07A6B, true);
        Component portal = Component.translatable(state.netherPortalRegistered()
                ? "screen.kingdoms.dimension.nether_portal_registered"
                : "screen.kingdoms.dimension.nether_portal_missing");
        graphics.drawString(font, portal, panelLeft + 8, panelTop + SECTION_NETHER_TOP + 62,
                state.netherPortalRegistered() ? 0xFF9A8F7A : 0xFFE07A6B, true);
    }

    private void renderSection(
            GuiGraphics graphics,
            boolean end,
            int sectionTop,
            boolean closed,
            boolean wipePending,
            int players
    ) {
        Component stateLine = Component.translatable(
                "screen.kingdoms.dimension.state",
                Component.translatable(end ? "kingdoms.dimension.name.end" : "kingdoms.dimension.name.nether"),
                Component.translatable(closed
                        ? "screen.kingdoms.dimension.state_closed"
                        : "screen.kingdoms.dimension.state_open"),
                players
        );
        graphics.drawString(font, stateLine, panelLeft + 8, panelTop + sectionTop, 0xFFE8DCC0, true);
        Component wipeLine = Component.translatable(wipePending
                ? "screen.kingdoms.dimension.wipe_pending"
                : "screen.kingdoms.dimension.wipe_none");
        graphics.drawString(font, wipeLine, panelLeft + 8, panelTop + sectionTop + 12,
                wipePending ? 0xFFE07A6B : 0xFF9A8F7A, true);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFFC9A24C);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF2B2E38);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatDuration(long seconds) {
        long safe = Math.max(0L, seconds);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", safe / 60L, safe % 60L);
    }
}
