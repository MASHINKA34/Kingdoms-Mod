package com.geydev.kalfactions.client.screen;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class KingdomsConfirmScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int TEXT_MARGIN = 14;

    private final Screen parent;
    private final Component message;
    private final Runnable onConfirm;

    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private List<FormattedCharSequence> lines = List.of();

    public KingdomsConfirmScreen(Screen parent, Component title, Component message, Runnable onConfirm) {
        super(title);
        this.parent = parent;
        this.message = message;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        lines = font.split(message, PANEL_WIDTH - TEXT_MARGIN * 2);
        panelHeight = 34 + lines.size() * 10 + 40;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.confirm_yes"),
                button -> {
                    minecraft.setScreen(parent);
                    onConfirm.run();
                },
                panelLeft + PANEL_WIDTH / 2 - 104,
                panelTop + panelHeight - 30,
                100,
                20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                panelLeft + PANEL_WIDTH / 2 + 4,
                panelTop + panelHeight - 30,
                100,
                20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);
        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            graphics.drawString(font, line,
                    panelLeft + (PANEL_WIDTH - font.width(line)) / 2,
                    panelTop + 28 + index * 10,
                    0xFFE8DFCB, true);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + panelHeight + 1, 0xFFC9A24C);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, 0xFF2B2E38);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
