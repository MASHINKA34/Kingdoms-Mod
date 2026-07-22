package com.geydev.kalfactions.integration.xaero.archive.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class XaeroArchiveScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 184;
    private final BlockPos anchor;
    private final ResourceLocation dimension;
    private XaeroArchiveClient.TransferState transfer = XaeroArchiveClient.state();
    private Button uploadButton;
    private Button downloadButton;
    private Button cancelButton;
    private int panelLeft;
    private int panelTop;
    private long localBytes;
    private long localUncompressedBytes;
    private int localRegions;
    private int localTiles;
    private long factionBytes;
    private long factionUncompressedBytes;
    private int factionRegions;
    private int factionTiles;
    private String statsMessageKey = "";

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
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        uploadButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.kingdoms.xaero_archive.upload"),
                button -> XaeroArchiveClient.startUpload(anchor)
        ).bounds(panelLeft + 10, panelTop + 102, 126, 20).build());
        downloadButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.kingdoms.xaero_archive.download"),
                button -> XaeroArchiveClient.startDownload(anchor)
        ).bounds(panelLeft + 144, panelTop + 102, 126, 20).build());
        cancelButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.kingdoms.xaero_archive.cancel"),
                button -> XaeroArchiveClient.cancel()
        ).bounds(panelLeft + 10, panelTop + 130, 126, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(panelLeft + 144, panelTop + 130, 126, 20).build());
        XaeroArchiveClient.setListener(this::updateTransfer);
        XaeroArchiveClient.requestStats(anchor, this::updateStats);
        updateButtons();
    }

    private void updateStats(XaeroArchiveClient.ArchiveStats stats) {
        localBytes = stats.local().compressedSize();
        localUncompressedBytes = stats.local().uncompressedSize();
        localRegions = stats.local().regionCount();
        localTiles = stats.local().tileCount();
        factionBytes = stats.faction().compressedSize();
        factionUncompressedBytes = stats.faction().uncompressedSize();
        factionRegions = stats.faction().regionCount();
        factionTiles = stats.faction().tileCount();
        statsMessageKey = stats.messageKey();
    }

    private void updateTransfer(XaeroArchiveClient.TransferState next) {
        transfer = next;
        if (next.phase().equals("upload") || next.phase().equals("snapshot")) {
            localBytes = next.totalBytes();
            localRegions = next.regionCount();
            localTiles = next.tileCount();
        }
        if (next.phase().equals("download") || next.phase().equals("import")) {
            factionBytes = next.totalBytes();
            factionRegions = next.regionCount();
            factionTiles = next.tileCount();
        }
        updateButtons();
    }

    private void updateButtons() {
        if (uploadButton == null) {
            return;
        }
        boolean running = !transfer.terminal();
        uploadButton.active = !running;
        downloadButton.active = !running;
        cancelButton.active = running;
    }

    @Override
    public void removed() {
        XaeroArchiveClient.setListener(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, panelLeft + PANEL_WIDTH / 2, panelTop + 10, 0xFFF3D58B);
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.dimension", dimension.toString()),
                panelLeft + 10, panelTop + 30, 0xFFE8DCC0, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.local_stats",
                        formatBytes(localBytes), formatBytes(localUncompressedBytes), localRegions, localTiles),
                panelLeft + 10, panelTop + 47, 0xFFD0C4AA, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.xaero_archive.faction_stats",
                        formatBytes(factionBytes), formatBytes(factionUncompressedBytes), factionRegions, factionTiles),
                panelLeft + 10, panelTop + 61, 0xFFD0C4AA, false);
        String messageKey = transfer.phase().equals("idle") && !statsMessageKey.isBlank()
                ? statsMessageKey : transfer.messageKey();
        graphics.drawString(font, Component.translatable(messageKey),
                panelLeft + 10, panelTop + 78, transfer.successful() ? 0xFF8FD98F : 0xFFE07A6B, false);
        int progressWidth = PANEL_WIDTH - 20;
        int filled = (int) Math.round(progressWidth * transfer.progress());
        graphics.fill(panelLeft + 10, panelTop + 91, panelLeft + 10 + progressWidth, panelTop + 96, 0xFF17191F);
        graphics.fill(panelLeft + 10, panelTop + 91, panelLeft + 10 + filled, panelTop + 96, 0xFFC9A24C);
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

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kibibytes = bytes / 1024.0D;
        if (kibibytes < 1024.0D) {
            return String.format(java.util.Locale.ROOT, "%.1f KiB", kibibytes);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MiB", kibibytes / 1024.0D);
    }
}
