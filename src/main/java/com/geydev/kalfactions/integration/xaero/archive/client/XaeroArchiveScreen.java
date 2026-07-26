package com.geydev.kalfactions.integration.xaero.archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class XaeroArchiveScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PADDING = 12;
    private static final int LINE_HEIGHT = 10;
    private static final int ROW_HEIGHT = 11;
    private static final int BUTTON_GAP = 8;
    private static final int BORDER_COLOR = 0xFFC9A24C;
    private static final int PANEL_COLOR = 0xFF2B2E38;
    private static final int DIVIDER_COLOR = 0x66C9A24C;
    private static final int TITLE_COLOR = 0xFFF3D58B;
    private static final int TEXT_COLOR = 0xFFE8DCC0;
    private static final int MUTED_COLOR = 0xFF9C9484;
    private static final int SUCCESS_COLOR = 0xFF8FD98F;
    private static final int ERROR_COLOR = 0xFFE07A6B;
    private static final int TRACK_COLOR = 0xFF17191F;

    private final BlockPos anchor;
    private final ResourceLocation dimension;
    private XaeroArchiveClient.TransferState transfer = XaeroArchiveClient.state();
    private XaeroArchiveClient.DataStats local = new XaeroArchiveClient.DataStats(0, 0, 0, 0);
    private XaeroArchiveClient.DataStats faction = new XaeroArchiveClient.DataStats(0, 0, 0, 0);
    private String statsMessageKey = "";
    private boolean statsRequested;
    private UUID refreshedSession;
    private Button takeButton;
    private Button shareButton;
    private Button cancelButton;
    private List<FormattedCharSequence> hintLines = List.of();
    private List<FormattedCharSequence> statusLines = List.of();
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int titleY;
    private int dimensionY;
    private int hintY;
    private int firstDividerY;
    private int columnHeaderY;
    private int statRowY;
    private int secondDividerY;
    private int statusY;
    private int progressY;
    private int firstButtonRowY;
    private int secondButtonRowY;

    private XaeroArchiveScreen(BlockPos anchor, ResourceLocation dimension) {
        super(Component.translatable("screen.kingdoms.xaero_archive.title"));
        this.anchor = anchor.immutable();
        this.dimension = dimension;
    }

    public static void open(BlockPos anchor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new XaeroArchiveScreen(anchor, minecraft.level.dimension().location()));
    }

    @Override
    protected void init() {
        int contentWidth = PANEL_WIDTH - PADDING * 2;
        hintLines = font.split(Component.translatable("screen.kingdoms.xaero_archive.hint"), contentWidth);
        int cursor = PADDING;
        titleY = cursor;
        cursor += 16;
        dimensionY = cursor;
        cursor += 13;
        hintY = cursor;
        cursor += hintLines.size() * LINE_HEIGHT + 6;
        firstDividerY = cursor;
        cursor += 9;
        columnHeaderY = cursor;
        cursor += 14;
        statRowY = cursor;
        cursor += ROW_HEIGHT * 3 + 6;
        secondDividerY = cursor;
        cursor += 9;
        statusY = cursor;
        cursor += LINE_HEIGHT * 3 + 5;
        progressY = cursor;
        cursor += 11;
        firstButtonRowY = cursor;
        cursor += 24;
        secondButtonRowY = cursor;
        cursor += 20;
        panelHeight = cursor + PADDING;
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - panelHeight) / 2;

        int buttonWidth = (contentWidth - BUTTON_GAP) / 2;
        int leftColumn = panelLeft + PADDING;
        int rightColumn = leftColumn + buttonWidth + BUTTON_GAP;
        takeButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.kingdoms.xaero_archive.take"),
                button -> startTransfer(true)
        ).bounds(leftColumn, panelTop + firstButtonRowY, buttonWidth, 20).build());
        shareButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.kingdoms.xaero_archive.share"),
                button -> startTransfer(false)
        ).bounds(rightColumn, panelTop + firstButtonRowY, buttonWidth, 20).build());
        cancelButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.kingdoms.xaero_archive.cancel"),
                button -> XaeroArchiveClient.cancel()
        ).bounds(leftColumn, panelTop + secondButtonRowY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(rightColumn, panelTop + secondButtonRowY, buttonWidth, 20).build());

        transfer = XaeroArchiveClient.state();
        refreshedSession = transfer.sessionId();
        XaeroArchiveClient.setListener(this::updateTransfer);
        if (!statsRequested) {
            statsRequested = true;
            XaeroArchiveClient.requestStats(anchor, this::updateStats);
        }
        updateButtons();
    }

    private void startTransfer(boolean take) {
        statsMessageKey = "";
        if (take) {
            XaeroArchiveClient.startDownload(anchor);
        } else {
            XaeroArchiveClient.startUpload(anchor);
        }
    }

    private void updateStats(XaeroArchiveClient.ArchiveStats stats) {
        if (stats.local() != null) {
            local = stats.local();
        }
        if (stats.faction() != null) {
            faction = stats.faction();
        }
        statsMessageKey = stats.messageKey();
    }

    private void updateTransfer(XaeroArchiveClient.TransferState next) {
        boolean completed = next.terminal()
                && next.successful()
                && next.sessionId() != null
                && !next.sessionId().equals(refreshedSession);
        transfer = next;
        updateButtons();
        if (completed) {
            refreshedSession = next.sessionId();
            XaeroArchiveClient.requestStats(anchor, this::updateStats);
        }
    }

    private void updateButtons() {
        if (takeButton == null) {
            return;
        }
        boolean running = !transfer.terminal();
        takeButton.active = !running;
        shareButton.active = !running;
        cancelButton.active = running;
    }

    @Override
    public void removed() {
        XaeroArchiveClient.setListener(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int contentLeft = panelLeft + PADDING;
        int contentRight = panelLeft + PANEL_WIDTH - PADDING;
        int contentWidth = contentRight - contentLeft;

        graphics.drawCenteredString(font, title, panelLeft + PANEL_WIDTH / 2, panelTop + titleY, TITLE_COLOR);
        graphics.drawCenteredString(font,
                Component.translatable("screen.kingdoms.xaero_archive.dimension", dimension.toString()),
                panelLeft + PANEL_WIDTH / 2, panelTop + dimensionY, MUTED_COLOR);
        for (int line = 0; line < hintLines.size(); line++) {
            graphics.drawString(font, hintLines.get(line), contentLeft, panelTop + hintY + line * LINE_HEIGHT,
                    TEXT_COLOR, false);
        }
        graphics.fill(contentLeft, panelTop + firstDividerY, contentRight, panelTop + firstDividerY + 1, DIVIDER_COLOR);

        int columnWidth = (contentWidth - BUTTON_GAP) / 2;
        int localColumn = contentLeft;
        int factionColumn = contentLeft + columnWidth + BUTTON_GAP;
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.column_local"),
                localColumn, panelTop + columnHeaderY, TITLE_COLOR, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.column_faction"),
                factionColumn, panelTop + columnHeaderY, TITLE_COLOR, false);
        drawStatColumn(graphics, localColumn, local);
        drawStatColumn(graphics, factionColumn, faction);
        graphics.fill(contentLeft, panelTop + secondDividerY, contentRight, panelTop + secondDividerY + 1, DIVIDER_COLOR);

        boolean statsError = transfer.phase().equals("idle") && !statsMessageKey.isBlank();
        String messageKey = statsError ? statsMessageKey : transfer.messageKey();
        int statusColor = !statsError && transfer.successful() ? SUCCESS_COLOR : ERROR_COLOR;
        statusLines = font.split(Component.translatable(messageKey), contentWidth);
        for (int line = 0; line < Math.min(3, statusLines.size()); line++) {
            graphics.drawString(font, statusLines.get(line), contentLeft, panelTop + statusY + line * LINE_HEIGHT,
                    statusColor, false);
        }

        int filled = (int) Math.round(contentWidth * transfer.progress());
        graphics.fill(contentLeft, panelTop + progressY, contentRight, panelTop + progressY + 5, TRACK_COLOR);
        graphics.fill(contentLeft, panelTop + progressY, contentLeft + filled, panelTop + progressY + 5, BORDER_COLOR);
    }

    private void drawStatColumn(GuiGraphics graphics, int x, XaeroArchiveClient.DataStats stats) {
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.row_regions", stats.regionCount()),
                x, panelTop + statRowY, TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.row_tiles", stats.tileCount()),
                x, panelTop + statRowY + ROW_HEIGHT, TEXT_COLOR, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.row_size",
                        formatBytes(stats.compressedSize())),
                x, panelTop + statRowY + ROW_HEIGHT * 2, MUTED_COLOR, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + panelHeight + 1, BORDER_COLOR);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, PANEL_COLOR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kibibytes = bytes / 1024.0D;
        if (kibibytes < 1024.0D) {
            return String.format(Locale.ROOT, "%.1f KiB", kibibytes);
        }
        return String.format(Locale.ROOT, "%.1f MiB", kibibytes / 1024.0D);
    }
}
