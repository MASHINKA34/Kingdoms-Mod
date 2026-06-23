package com.geydev.kalfactions.client.screen;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class SelectEntryScreen extends Screen {
    public record Entry(String value, String skinName, Component subtitle, int swatchColor, boolean enabled, ResourceLocation icon) {
        public Entry(String value, String skinName, Component subtitle, int swatchColor, boolean enabled) {
            this(value, skinName, subtitle, swatchColor, enabled, null);
        }

        public static Entry player(String name, Component subtitle, boolean enabled) {
            return new Entry(name, name, subtitle, 0, enabled);
        }

        public static Entry swatch(String name, int color) {
            return new Entry(name, null, null, color, true);
        }

        public static Entry icon(String name, Component subtitle, ResourceLocation icon, boolean enabled) {
            return new Entry(name, null, subtitle, 0, enabled, icon);
        }
    }

    private static final int PANEL_WIDTH = 240;
    private static final int COMPACT_ROW_HEIGHT = 26;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_TOP_OFFSET = 26;
    private static final int MIN_ICON_SIZE = 22;
    private static final int MAX_ICON_SIZE = 34;
    private static final int MAX_SUBTITLE_LINES = 3;
    private static final int SCROLLBAR_WIDTH = 4;

    private final Screen parent;
    private final List<Entry> entries;
    private final Consumer<Entry> onPick;
    private final Component disabledNotice;

    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int scrollOffset;
    private int rowHeight = COMPACT_ROW_HEIGHT;
    private boolean scrollBarDragging;
    private String noticeText = "";
    private long noticeShownAt;

    public SelectEntryScreen(
            Screen parent,
            Component title,
            List<Entry> entries,
            Component disabledNotice,
            Consumer<Entry> onPick
    ) {
        super(title);
        this.parent = parent;
        this.entries = entries;
        this.disabledNotice = disabledNotice;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        rowHeight = computeRowHeight();
        int rows = Math.min(VISIBLE_ROWS, Math.max(1, entries.size()));
        panelHeight = LIST_TOP_OFFSET + rows * rowHeight + 36;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 90) / 2, panelTop + panelHeight - 28, 90, 20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 9, 0xFFF3D58B, true);

        int shown = visibleEntryCount();
        for (int index = 0; index < shown; index++) {
            renderRow(graphics, entries.get(scrollOffset + index), rowTop(index), mouseX, mouseY);
        }
        if (entries.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.no_players");
            graphics.drawString(font, empty, panelLeft + (PANEL_WIDTH - font.width(empty)) / 2,
                    panelTop + LIST_TOP_OFFSET + 8, 0xFF8E8B83, true);
        }
        renderScrollbar(graphics);

        if (!noticeText.isEmpty() && System.currentTimeMillis() - noticeShownAt < 2200L) {
            String clipped = font.plainSubstrByWidth(noticeText, PANEL_WIDTH - 16);
            int textWidth = font.width(clipped);
            graphics.drawString(font, clipped, panelLeft + (PANEL_WIDTH - textWidth) / 2,
                    panelTop + panelHeight - 40, 0xFFE89090, true);
        }
    }

    private void renderRow(GuiGraphics graphics, Entry entry, int rowTop, int mouseX, int mouseY) {
        int rowLeft = panelLeft + 8;
        int rowRight = listRight();
        boolean hovered = entry.enabled()
                && mouseX >= rowLeft && mouseX < rowRight
                && mouseY >= rowTop && mouseY < rowTop + rowHeight - 2;
        int background = hovered ? 0x50FFFFFF : 0x28000000;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + rowHeight - 2, background);

        if (entry.skinName() != null) {
            PlayerSkin skin = resolveSkin(entry.skinName());
            PlayerFaceRenderer.draw(graphics, skin, rowLeft + 4, rowTop + 4, 16);
        } else if (entry.icon() != null) {
            int iconSize = iconSize();
            int iconY = rowTop + Math.max(2, (rowHeight - 2 - iconSize) / 2);
            graphics.blit(entry.icon(), rowLeft + 3, iconY, iconSize, iconSize,
                    0, 0, 64, 64, 64, 64);
        } else {
            graphics.fill(rowLeft + 4, rowTop + 4, rowLeft + 20, rowTop + 20, 0xFF1A140C);
            graphics.fill(rowLeft + 5, rowTop + 5, rowLeft + 19, rowTop + 19, 0xFF000000 | entry.swatchColor());
        }

        int nameColor = entry.enabled() ? 0xFFFFFFFF : 0xFF8E8B83;
        int textX = entry.icon() == null ? rowLeft + 26 : rowLeft + 32;
        int textWidth = Math.max(20, rowRight - textX - 4);
        int nameY = rowTop + (rowHeight > COMPACT_ROW_HEIGHT ? 5 : 3);
        graphics.drawString(font, font.plainSubstrByWidth(entry.value(), textWidth), textX, nameY, nameColor, true);
        if (entry.subtitle() != null) {
            List<FormattedCharSequence> lines = font.split(entry.subtitle(), textWidth);
            int lineLimit = subtitleLineLimit();
            int lineY = rowTop + (rowHeight > COMPACT_ROW_HEIGHT ? 18 : 14);
            for (int index = 0; index < Math.min(lineLimit, lines.size()); index++) {
                graphics.drawString(font, lines.get(index), textX, lineY + index * 10, 0xFF9A8F7A, true);
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (!hasScrollbar()) {
            return;
        }
        int trackLeft = panelLeft + PANEL_WIDTH - 12;
        int trackTop = listTop();
        int trackBottom = listBottom() - 2;
        int trackRight = trackLeft + SCROLLBAR_WIDTH;
        graphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0x44000000);
        int thumbTop = scrollbarThumbTop();
        int thumbHeight = scrollbarThumbHeight();
        graphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0xFFC9A24C);
    }

    private PlayerSkin resolveSkin(String playerName) {
        if (minecraft != null && minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerName);
            if (info != null) {
                return info.getSkin();
            }
        }
        return DefaultPlayerSkin.get(Util.NIL_UUID);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hasScrollbar() && mouseX >= panelLeft + PANEL_WIDTH - 14 && mouseX <= panelLeft + PANEL_WIDTH - 6
                    && mouseY >= listTop() && mouseY <= listBottom()) {
                scrollBarDragging = true;
                updateScrollFromMouse(mouseY);
                return true;
            }

            int shown = visibleEntryCount();
            for (int index = 0; index < shown; index++) {
                int rowTop = rowTop(index);
                if (mouseX >= panelLeft + 8 && mouseX < listRight()
                        && mouseY >= rowTop && mouseY < rowTop + rowHeight - 2) {
                    Entry entry = entries.get(scrollOffset + index);
                    if (!entry.enabled()) {
                        noticeText = disabledNotice == null ? "" : disabledNotice.getString();
                        noticeShownAt = System.currentTimeMillis();
                        return true;
                    }
                    onClose();
                    onPick.accept(entry);
                    return true;
                }
            }
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

    private int rowTop(int visibleIndex) {
        return panelTop + LIST_TOP_OFFSET + visibleIndex * rowHeight;
    }

    private int listTop() {
        return panelTop + LIST_TOP_OFFSET;
    }

    private int listBottom() {
        return listTop() + Math.min(VISIBLE_ROWS, Math.max(1, entries.size())) * rowHeight;
    }

    private int listRight() {
        return panelLeft + PANEL_WIDTH - (hasScrollbar() ? 18 : 8);
    }

    private int visibleEntryCount() {
        return Math.min(VISIBLE_ROWS, Math.max(0, entries.size() - scrollOffset));
    }

    private boolean hasScrollbar() {
        return entries.size() > VISIBLE_ROWS;
    }

    private int scrollbarThumbHeight() {
        int trackHeight = listBottom() - listTop() - 2;
        return Math.max(16, trackHeight * VISIBLE_ROWS / entries.size());
    }

    private int scrollbarThumbTop() {
        int trackTop = listTop();
        int trackHeight = listBottom() - listTop() - 2;
        int travel = Math.max(1, trackHeight - scrollbarThumbHeight());
        return trackTop + Math.round(travel * (scrollOffset / (float) maxScroll()));
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackTop = listTop();
        int trackHeight = listBottom() - listTop() - 2;
        int travel = Math.max(1, trackHeight - scrollbarThumbHeight());
        float progress = Mth.clamp((float) (mouseY - trackTop - scrollbarThumbHeight() / 2.0F) / travel, 0.0F, 1.0F);
        scrollOffset = Math.clamp(Math.round(progress * maxScroll()), 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, entries.size() - VISIBLE_ROWS);
    }

    private int computeRowHeight() {
        if (entries.stream().noneMatch(entry -> entry.icon() != null)) {
            return COMPACT_ROW_HEIGHT;
        }
        int textWidth = Math.max(20, PANEL_WIDTH - (entries.size() > VISIBLE_ROWS ? 18 : 8) - 8 - 32 - 4);
        int lines = 1;
        for (Entry entry : entries) {
            if (entry.icon() != null && entry.subtitle() != null) {
                lines = Math.max(lines, font.split(entry.subtitle(), textWidth).size());
            }
        }
        int subtitleLines = Math.min(MAX_SUBTITLE_LINES, Math.max(1, lines));
        return Math.max(COMPACT_ROW_HEIGHT, 24 + subtitleLines * 10);
    }

    private int iconSize() {
        return Mth.clamp(rowHeight - 10, MIN_ICON_SIZE, MAX_ICON_SIZE);
    }

    private int subtitleLineLimit() {
        return Math.max(1, Math.min(MAX_SUBTITLE_LINES, (rowHeight - 20) / 10));
    }
}
