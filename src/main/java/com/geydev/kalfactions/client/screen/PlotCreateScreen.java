package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.market.MarketPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlotCreateScreen extends Screen {
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 132;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private EditBox priceBox;
    private KingdomsButton createButton;
    private int panelLeft;
    private int panelTop;

    public PlotCreateScreen(int sizeX, int sizeY, int sizeZ) {
        super(Component.translatable("screen.kingdoms.plot_create_title"));
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        String previous = priceBox == null ? "" : priceBox.getValue();
        priceBox = new EditBox(
                font,
                panelLeft + 8,
                panelTop + 52,
                PANEL_WIDTH - 16,
                18,
                Component.translatable("screen.kingdoms.plot_price_hint")
        );
        priceBox.setFilter(text -> text.matches("\\d{0,9}"));
        priceBox.setHint(Component.translatable("screen.kingdoms.plot_price_hint"));
        priceBox.setValue(previous);
        priceBox.setResponder(text -> updateCreateButton());
        addRenderableWidget(priceBox);

        createButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.plot_create_confirm"),
                button -> {
                    long price = parsePrice();
                    if (price > 0L) {
                        PacketDistributor.sendToServer(new MarketPayloads.C2SCreatePlot(price));
                        onClose();
                    }
                },
                panelLeft + 8,
                panelTop + 78,
                PANEL_WIDTH - 16,
                20
        ));

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2,
                panelTop + PANEL_HEIGHT - 26,
                90,
                20
        ));
        updateCreateButton();
        setInitialFocus(priceBox);
    }

    private void updateCreateButton() {
        if (createButton != null) {
            createButton.active = parsePrice() > 0L;
        }
    }

    private long parsePrice() {
        if (priceBox == null || priceBox.getValue().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(priceBox.getValue());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.plot_create_size", sizeX, sizeY, sizeZ),
                panelLeft + 8, panelTop + 28, 0xFF9A8F7A, true);
        graphics.drawString(font,
                Component.translatable("screen.kingdoms.plot_price_hint"),
                panelLeft + 8, panelTop + 42, 0xFF9A8F7A, true);
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
}
