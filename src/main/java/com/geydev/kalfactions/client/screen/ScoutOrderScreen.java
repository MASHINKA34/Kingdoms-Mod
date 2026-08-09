package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ScoutAreaSelection;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.integration.xaero.XaeroMaps;
import com.geydev.kalfactions.scout.ScoutPayloads;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ScoutOrderScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 18;
    private static final int ROW_HEIGHT = 42;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFEDE6D6;
    private static final int MUTED = 0xFFB6AA92;
    private static final int DENIED = 0xFFE29388;

    private final ScoutPayloads.S2COpenScout data;
    private int panelLeft;
    private int panelTop;

    private ScoutOrderScreen(ScoutPayloads.S2COpenScout data) {
        super(Component.translatable("screen.kingdoms.scout.title"));
        this.data = data;
    }

    public static void handle(ScoutPayloads.S2COpenScout payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new ScoutOrderScreen(payload)));
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        List<ScoutPayloads.PackageEntry> packages = data.packages();
        for (int index = 0; index < packages.size(); index++) {
            ScoutPayloads.PackageEntry entry = packages.get(index);
            boolean affordable = data.treasuryBalance() >= entry.price();
            KingdomsButton button = KingdomsButton.create(
                    Component.translatable("screen.kingdoms.scout.pick"),
                    ignored -> choose(entry),
                    panelLeft + PANEL_WIDTH - 96,
                    panelTop + 44 + index * ROW_HEIGHT + 8,
                    78,
                    20
            );
            button.active = data.canOrder() && affordable;
            addRenderableWidget(button);
        }
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + PANEL_WIDTH - 78,
                panelTop + PANEL_HEIGHT - 26,
                66,
                20
        ));
    }

    private void choose(ScoutPayloads.PackageEntry entry) {
        ScoutAreaSelection.begin(entry);
        if (!XaeroMaps.openScoutPicker()) {
            Minecraft.getInstance().setScreen(new ScoutAreaScreen(entry));
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - font.width(title)) / 2, panelTop + 12, GOLD, true);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.scout.treasury",
                        NumismaticsEconomy.format(data.treasuryBalance())),
                panelLeft + CONTENT_LEFT,
                panelTop + 28,
                MUTED,
                false
        );

        List<ScoutPayloads.PackageEntry> packages = data.packages();
        for (int index = 0; index < packages.size(); index++) {
            ScoutPayloads.PackageEntry entry = packages.get(index);
            int rowTop = panelTop + 44 + index * ROW_HEIGHT;
            boolean affordable = data.treasuryBalance() >= entry.price();
            graphics.fill(
                    panelLeft + CONTENT_LEFT - 4,
                    rowTop,
                    panelLeft + PANEL_WIDTH - CONTENT_LEFT + 4,
                    rowTop + ROW_HEIGHT - 6,
                    0x33000000
            );
            graphics.drawString(
                    font,
                    Component.translatable("screen.kingdoms.scout.package." + entry.ordinal()),
                    panelLeft + CONTENT_LEFT,
                    rowTop + 5,
                    GOLD,
                    false
            );
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.kingdoms.scout.size",
                            entry.sizeChunks(),
                            entry.sizeChunks(),
                            entry.sizeChunks() * 16,
                            entry.sizeChunks() * 16
                    ),
                    panelLeft + CONTENT_LEFT,
                    rowTop + 17,
                    TEXT,
                    false
            );
            graphics.drawString(
                    font,
                    Component.translatable(
                            "screen.kingdoms.scout.terms",
                            entry.durationMinutes(),
                            NumismaticsEconomy.format(entry.price())
                    ),
                    panelLeft + CONTENT_LEFT,
                    rowTop + 28,
                    affordable ? MUTED : DENIED,
                    false
            );
        }

        if (!data.canOrder()) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.kingdoms.scout.not_officer"),
                    panelLeft + CONTENT_LEFT,
                    panelTop + PANEL_HEIGHT - 22,
                    DENIED,
                    false
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
