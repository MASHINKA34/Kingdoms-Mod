package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.news.NewsManager;
import com.geydev.kalfactions.news.NewsPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NewsEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 226;

    private final Screen parent;
    private EditBox titleBox;
    private MultiLineEditBox bodyBox;
    private KingdomsButton publishButton;
    private String titleValue = "";
    private String bodyValue = "";
    private int panelLeft;
    private int panelTop;

    public NewsEditorScreen(Screen parent) {
        super(Component.translatable("screen.kingdoms.news_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        titleBox = new EditBox(
                font,
                panelLeft + 12,
                panelTop + 40,
                PANEL_WIDTH - 24,
                20,
                Component.translatable("screen.kingdoms.news_editor.headline")
        );
        titleBox.setMaxLength(NewsManager.MAX_TITLE_LENGTH);
        titleBox.setHint(Component.translatable("screen.kingdoms.news_editor.headline_hint"));
        titleBox.setValue(titleValue);
        titleBox.setResponder(value -> {
            titleValue = value;
            refreshPublishButton();
        });
        addRenderableWidget(titleBox);

        bodyBox = new MultiLineEditBox(
                font,
                panelLeft + 12,
                panelTop + 78,
                PANEL_WIDTH - 24,
                110,
                Component.translatable("screen.kingdoms.news_editor.body_hint"),
                Component.translatable("screen.kingdoms.news_editor.body")
        );
        bodyBox.setCharacterLimit(NewsManager.MAX_BODY_LENGTH);
        bodyBox.setValue(bodyValue);
        bodyBox.setValueListener(value -> {
            bodyValue = value;
            refreshPublishButton();
        });
        addRenderableWidget(bodyBox);

        int buttonsY = panelTop + PANEL_HEIGHT - 28;
        int half = (PANEL_WIDTH - 30) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                panelLeft + 12, buttonsY, half, 20
        ));
        publishButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.news_editor.publish"),
                button -> publish(),
                panelLeft + PANEL_WIDTH - 12 - half, buttonsY, half, 20
        ));
        refreshPublishButton();
    }

    private void refreshPublishButton() {
        publishButton.active = !titleValue.isBlank() && !bodyValue.isBlank();
    }

    private void publish() {
        if (titleValue.isBlank() || bodyValue.isBlank()) {
            return;
        }
        PacketDistributor.sendToServer(new NewsPayloads.C2SPublishNews(titleValue.strip(), bodyValue.strip()));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - font.width(title)) / 2, panelTop + 9, 0xFFF3D58B, true);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.news_editor.headline"),
                panelLeft + 12,
                panelTop + 29,
                0xFFC9A24C,
                true
        );
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.news_editor.body"),
                panelLeft + 12,
                panelTop + 67,
                0xFFC9A24C,
                true
        );
        String counter = bodyValue.length() + " / " + NewsManager.MAX_BODY_LENGTH;
        graphics.drawString(
                font,
                counter,
                panelLeft + PANEL_WIDTH - 12 - font.width(counter),
                panelTop + 67,
                0xFF9A8F7A,
                true
        );
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
