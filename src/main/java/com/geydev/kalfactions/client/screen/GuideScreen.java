package com.geydev.kalfactions.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class GuideScreen extends Screen {
    private static final String[] SECTIONS = {
            "factions",
            "territory",
            "influence",
            "research",
            "bonuses",
            "war",
            "alliances",
            "archive",
            "outposts",
            "trade",
            "world",
            "commands"
    };

    private static final int PANEL_WIDTH = 362;
    private static final int PANEL_HEIGHT = 236;
    private static final int LINE_HEIGHT = 10;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFEDE6D6;
    private static final int MUTED = 0xFFB6AA92;

    private final Screen parent;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int selected;
    private int scrollOffset;
    private boolean scrollBarDragging;

    public GuideScreen(Screen parent) {
        super(Component.translatable("screen.kingdoms.guide.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;

        int buttonTop = panelTop + 28;
        for (int index = 0; index < SECTIONS.length; index++) {
            int section = index;
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("kingdoms.guide.section." + SECTIONS[index]),
                    button -> select(section),
                    panelLeft + 10,
                    buttonTop + index * 17,
                    132,
                    15
            ));
        }
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + PANEL_WIDTH - 74,
                panelTop + PANEL_HEIGHT - 24,
                64,
                20
        ));
        rebuildLines();
    }

    private void select(int section) {
        if (selected != section) {
            selected = section;
            scrollOffset = 0;
            rebuildLines();
        }
    }

    private void rebuildLines() {
        lines.clear();
        Component body = Component.translatable("kingdoms.guide.body." + SECTIONS[selected]);
        lines.addAll(font.split(body, textWidth()));
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.fill(textLeft() - 8, panelTop + 26, textLeft() - 7, panelTop + PANEL_HEIGHT - 10, 0x66C9A24C);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, GOLD, true);

        Component heading = Component.translatable("kingdoms.guide.section." + SECTIONS[selected]);
        graphics.drawString(font, heading, textLeft(), panelTop + 28, GOLD, true);

        int top = contentTop();
        int bottom = contentBottom();
        graphics.enableScissor(textLeft(), top, textLeft() + textWidth(), bottom);
        int visible = visibleLines();
        for (int index = 0; index < visible && scrollOffset + index < lines.size(); index++) {
            graphics.drawString(
                    font,
                    lines.get(scrollOffset + index),
                    textLeft(),
                    top + index * LINE_HEIGHT,
                    TEXT,
                    false
            );
        }
        graphics.disableScissor();
        renderScrollbar(graphics);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll() <= 0) {
            return;
        }
        int trackLeft = panelLeft + PANEL_WIDTH - 11;
        int trackTop = contentTop();
        int trackBottom = contentBottom();
        graphics.fill(trackLeft, trackTop, trackLeft + SCROLLBAR_WIDTH, trackBottom, 0x44000000);
        int thumbHeight = Math.max(16, (trackBottom - trackTop) * visibleLines() / Math.max(1, lines.size()));
        int travel = Math.max(1, (trackBottom - trackTop) - thumbHeight);
        int thumbTop = trackTop + Math.round(travel * (scrollOffset / (float) maxScroll()));
        graphics.fill(trackLeft, thumbTop, trackLeft + SCROLLBAR_WIDTH, thumbTop + thumbHeight, KingdomsPanel.BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0
                && mouseX >= panelLeft + PANEL_WIDTH - 13 && mouseX <= panelLeft + PANEL_WIDTH - 5
                && mouseY >= contentTop() && mouseY <= contentBottom()) {
            scrollBarDragging = true;
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollBarDragging) {
            scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && scrollBarDragging) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int updated = Math.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxScroll());
        if (updated != scrollOffset) {
            scrollOffset = updated;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackTop = contentTop();
        int trackHeight = contentBottom() - contentTop();
        int thumbHeight = Math.max(16, trackHeight * visibleLines() / Math.max(1, lines.size()));
        int travel = Math.max(1, trackHeight - thumbHeight);
        float progress = Mth.clamp((float) (mouseY - trackTop - thumbHeight / 2.0F) / travel, 0.0F, 1.0F);
        scrollOffset = Math.clamp(Math.round(progress * maxScroll()), 0, maxScroll());
    }

    private int textLeft() {
        return panelLeft + 150;
    }

    private int textWidth() {
        return PANEL_WIDTH - 150 - 20;
    }

    private int contentTop() {
        return panelTop + 42;
    }

    private int contentBottom() {
        return panelTop + PANEL_HEIGHT - 30;
    }

    private int visibleLines() {
        return Math.max(1, (contentBottom() - contentTop()) / LINE_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, lines.size() - visibleLines());
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
