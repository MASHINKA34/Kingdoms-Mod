package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class WarArchiveScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_MAX_HEIGHT = 300;
    private static final int TITLE_HEIGHT = 28;
    private static final int BOTTOM_HEIGHT = 36;
    private static final int ROW_HEIGHT = 78;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int BORDER = 0xFFC9A24C;
    private static final int PANEL = 0xFF2B2E38;
    private static final int ROW = 0x33202028;
    private static final int ROW_HOVER = 0x44FFFFFF;
    private static final int TEXT = 0xFFF1EEE4;
    private static final int GOLD = 0xFFF3D58B;
    private static final int MUTED = 0xFFB6AA92;
    private static final int BAD = 0xFFE29388;
    private static final int GOOD = 0xFF91D69B;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final List<FactionPayloads.WarRecordView> records = new ArrayList<>();
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int scrollOffset;
    private boolean scrollBarDragging;

    public WarArchiveScreen(Screen parent, List<FactionPayloads.WarRecordView> records) {
        super(Component.translatable("screen.kingdoms.war_archive.title"));
        this.parent = parent;
        acceptData(records);
    }

    public void acceptData(List<FactionPayloads.WarRecordView> records) {
        this.records.clear();
        if (records != null) {
            this.records.addAll(records);
        }
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, Math.max(240, width - 20));
        panelHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(170, height - 28));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll());
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + (panelWidth - 90) / 2,
                panelTop + panelHeight - 27,
                90,
                20
        ));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + panelWidth + 1, panelTop + panelHeight + 1, BORDER);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, PANEL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (panelWidth - titleWidth) / 2, panelTop + 10, GOLD, true);

        int listLeft = panelLeft + 10;
        int listRight = listRight();
        graphics.enableScissor(listLeft, listTop(), listRight, listBottom());
        int shown = visibleRecordCount();
        for (int index = 0; index < shown; index++) {
            renderRecord(graphics, records.get(scrollOffset + index), rowTop(index), mouseX, mouseY);
        }
        graphics.disableScissor();

        if (records.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.war_archive.empty");
            graphics.drawString(
                    font,
                    empty,
                    panelLeft + (panelWidth - font.width(empty)) / 2,
                    listTop() + 22,
                    MUTED,
                    true
            );
        }
        renderScrollbar(graphics);
    }

    private void renderRecord(
            GuiGraphics graphics,
            FactionPayloads.WarRecordView record,
            int rowTop,
            int mouseX,
            int mouseY
    ) {
        int rowLeft = panelLeft + 10;
        int rowRight = listRight();
        boolean hovered = mouseX >= rowLeft && mouseX < rowRight && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT - 4;
        graphics.fill(rowLeft, rowTop, rowRight, rowTop + ROW_HEIGHT - 4, hovered ? ROW_HOVER : ROW);

        int textX = rowLeft + 7;
        int textWidth = rowRight - textX - 7;
        drawClipped(graphics, typeName(record), textX, rowTop + 6, textWidth, GOLD);
        drawClipped(graphics, sides(record), textX, rowTop + 19, textWidth, TEXT);
        drawClipped(graphics, outcome(record), textX, rowTop + 32, textWidth, outcomeColor(record));
        drawClipped(graphics, scoreLine(record), textX, rowTop + 45, textWidth, MUTED);

        List<FormattedCharSequence> reasonLines = font.split(Component.literal(reason(record)), textWidth);
        for (int index = 0; index < Math.min(2, reasonLines.size()); index++) {
            graphics.drawString(font, reasonLines.get(index), textX, rowTop + 58 + index * 9, MUTED, true);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (!hasScrollbar()) {
            return;
        }
        int trackLeft = panelLeft + panelWidth - 12;
        int trackTop = listTop();
        int trackBottom = listBottom() - 2;
        int trackRight = trackLeft + SCROLLBAR_WIDTH;
        graphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0x44000000);
        int thumbTop = scrollbarThumbTop();
        graphics.fill(trackLeft, thumbTop, trackRight, thumbTop + scrollbarThumbHeight(), BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hasScrollbar()
                && mouseX >= panelLeft + panelWidth - 14 && mouseX <= panelLeft + panelWidth - 6
                && mouseY >= listTop() && mouseY <= listBottom()) {
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

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawClipped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text, width), x, y, color, true);
    }

    private String typeName(FactionPayloads.WarRecordView record) {
        return Component.translatable("kingdoms.war.type." + record.typeId()).getString();
    }

    private String sides(FactionPayloads.WarRecordView record) {
        return Component.translatable(
                "screen.kingdoms.war_archive.sides",
                side(record.attackerLead(), record.attackerAllies()),
                side(record.defenderLead(), record.defenderAllies())
        ).getString();
    }

    private String side(String lead, List<String> allies) {
        String name = lead == null || lead.isBlank()
                ? Component.translatable("screen.kingdoms.war_archive.unknown").getString()
                : lead;
        int allyCount = allies == null ? 0 : allies.size();
        return allyCount <= 0 ? name : Component.translatable("screen.kingdoms.war_archive.side_allies", name, allyCount).getString();
    }

    private String outcome(FactionPayloads.WarRecordView record) {
        if ("VICTORY".equals(record.outcome()) && !record.winnerName().isBlank()) {
            return Component.translatable("screen.kingdoms.war_archive.victory", record.winnerName()).getString();
        }
        return Component.translatable("screen.kingdoms.war_archive.draw").getString();
    }

    private int outcomeColor(FactionPayloads.WarRecordView record) {
        return "VICTORY".equals(record.outcome()) ? GOOD : BAD;
    }

    private String scoreLine(FactionPayloads.WarRecordView record) {
        return Component.translatable(
                "screen.kingdoms.war_archive.score_date",
                record.attackerPoints(),
                record.defenderPoints(),
                date(record.endedAtMillis()),
                duration(record.endedAtMillis() - record.startedAtMillis())
        ).getString();
    }

    private String reason(FactionPayloads.WarRecordView record) {
        if (record.reason() == null || record.reason().isBlank()) {
            return Component.translatable("screen.kingdoms.war_archive.no_reason").getString();
        }
        return Component.translatable("screen.kingdoms.war_archive.reason", record.reason()).getString();
    }

    private static String date(long millis) {
        if (millis <= 0L) {
            return "-";
        }
        return DATE_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private String duration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        if (days > 0L) {
            return Component.translatable("screen.kingdoms.war_archive.duration_days", days, hours).getString();
        }
        if (hours > 0L) {
            return Component.translatable("screen.kingdoms.war_archive.duration_hours", hours, minutes).getString();
        }
        return Component.translatable("screen.kingdoms.war_archive.duration_minutes", Math.max(1L, minutes)).getString();
    }

    private int rowTop(int visibleIndex) {
        return listTop() + visibleIndex * ROW_HEIGHT;
    }

    private int listTop() {
        return panelTop + TITLE_HEIGHT;
    }

    private int listBottom() {
        return panelTop + panelHeight - BOTTOM_HEIGHT;
    }

    private int listRight() {
        return panelLeft + panelWidth - (hasScrollbar() ? 18 : 10);
    }

    private int visibleRecordCount() {
        return Math.min(visibleRows(), Math.max(0, records.size() - scrollOffset));
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT);
    }

    private boolean hasScrollbar() {
        return records.size() > visibleRows();
    }

    private int maxScroll() {
        return Math.max(0, records.size() - visibleRows());
    }

    private int scrollbarThumbHeight() {
        int trackHeight = listBottom() - listTop() - 2;
        return Math.max(16, trackHeight * visibleRows() / Math.max(1, records.size()));
    }

    private int scrollbarThumbTop() {
        int trackTop = listTop();
        int trackHeight = listBottom() - listTop() - 2;
        int travel = Math.max(1, trackHeight - scrollbarThumbHeight());
        return trackTop + Math.round(travel * (scrollOffset / (float) Math.max(1, maxScroll())));
    }

    private void updateScrollFromMouse(double mouseY) {
        int trackTop = listTop();
        int trackHeight = listBottom() - listTop() - 2;
        int travel = Math.max(1, trackHeight - scrollbarThumbHeight());
        float progress = Mth.clamp((float) (mouseY - trackTop - scrollbarThumbHeight() / 2.0F) / travel, 0.0F, 1.0F);
        scrollOffset = Math.clamp(Math.round(progress * maxScroll()), 0, maxScroll());
    }
}
