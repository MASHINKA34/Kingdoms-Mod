package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.ScoutAreaSelection;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.scout.ScoutPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

public final class ScoutAreaScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int GRID_CELLS = 17;
    private static final int CELL_SIZE = 9;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFEDE6D6;
    private static final int MUTED = 0xFFB6AA92;

    private final ScoutPayloads.PackageEntry entry;
    private final int originChunkX;
    private final int originChunkZ;
    private int panLeft;
    private int panTop;
    private int gridLeft;
    private int gridTop;
    private boolean chosen;
    private int centreChunkX;
    private int centreChunkZ;
    private KingdomsButton confirmButton;

    public ScoutAreaScreen(ScoutPayloads.PackageEntry entry) {
        super(Component.translatable("screen.kingdoms.scout.area_title"));
        this.entry = entry;
        ChunkPos here = Minecraft.getInstance().player == null
                ? new ChunkPos(0, 0)
                : Minecraft.getInstance().player.chunkPosition();
        this.originChunkX = here.x;
        this.originChunkZ = here.z;
        this.centreChunkX = here.x;
        this.centreChunkZ = here.z;
    }

    @Override
    protected void init() {
        panLeft = (width - PANEL_WIDTH) / 2;
        panTop = (height - PANEL_HEIGHT) / 2;
        gridLeft = panLeft + 18;
        gridTop = panTop + 40;
        confirmButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.scout.confirm"),
                button -> {
                    ScoutAreaSelection.confirm(centreChunkX, centreChunkZ);
                    onClose();
                },
                panLeft + 190,
                panTop + 150,
                124,
                20
        ));
        confirmButton.active = chosen;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.scout.cancel"),
                button -> onClose(),
                panLeft + PANEL_WIDTH - 78,
                panTop + PANEL_HEIGHT - 26,
                66,
                20
        ));
    }

    @Override
    public void onClose() {
        ScoutAreaSelection.clear();
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panLeft, panTop, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, panLeft + (PANEL_WIDTH - font.width(title)) / 2, panTop + 12, GOLD, true);

        int half = GRID_CELLS / 2;
        int previewHalf = (entry.sizeChunks() - 1) / 2;
        int hoverChunkX = insideGrid(mouseX, mouseY) ? hoveredChunkX(mouseX) : centreChunkX;
        int hoverChunkZ = insideGrid(mouseX, mouseY) ? hoveredChunkZ(mouseY) : centreChunkZ;
        int previewChunkX = chosen ? centreChunkX : hoverChunkX;
        int previewChunkZ = chosen ? centreChunkZ : hoverChunkZ;

        for (int row = 0; row < GRID_CELLS; row++) {
            for (int column = 0; column < GRID_CELLS; column++) {
                int chunkX = originChunkX + column - half;
                int chunkZ = originChunkZ + row - half;
                int x = gridLeft + column * CELL_SIZE;
                int y = gridTop + row * CELL_SIZE;
                int parchment = ((chunkX * 31 + chunkZ * 17) & 1) == 0 ? 0xFF3A3E4A : 0xFF32363F;
                graphics.fill(x, y, x + CELL_SIZE - 1, y + CELL_SIZE - 1, parchment);
                boolean covered = Math.abs(chunkX - previewChunkX) <= previewHalf
                        && Math.abs(chunkZ - previewChunkZ) <= previewHalf;
                if (covered) {
                    graphics.fill(x + 1, y + 1, x + CELL_SIZE - 2, y + CELL_SIZE - 2,
                            chosen ? 0xFF3F8FB4 : 0xFF6E6244);
                }
                if (chunkX == originChunkX && chunkZ == originChunkZ) {
                    graphics.fill(x + 3, y + 3, x + CELL_SIZE - 4, y + CELL_SIZE - 4, 0xFFFFFFFF);
                }
            }
        }

        int infoX = panLeft + 190;
        graphics.drawString(font, Component.translatable(
                "screen.kingdoms.scout.size",
                entry.sizeChunks(),
                entry.sizeChunks(),
                entry.sizeChunks() * 16,
                entry.sizeChunks() * 16
        ), infoX, panTop + 44, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.kingdoms.scout.terms",
                entry.durationMinutes(),
                NumismaticsEconomy.format(entry.price())
        ), infoX, panTop + 56, MUTED, false);
        graphics.drawString(font, Component.translatable(
                "screen.kingdoms.scout.centre",
                previewChunkX,
                previewChunkZ
        ), infoX, panTop + 72, TEXT, false);
        graphics.drawWordWrap(
                font,
                Component.translatable("screen.kingdoms.scout.area_help"),
                infoX,
                panTop + 90,
                PANEL_WIDTH - 190 - 16,
                MUTED
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideGrid(mouseX, mouseY)) {
            centreChunkX = hoveredChunkX(mouseX);
            centreChunkZ = hoveredChunkZ(mouseY);
            chosen = true;
            if (confirmButton != null) {
                confirmButton.active = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return mouseX >= gridLeft
                && mouseY >= gridTop
                && mouseX < gridLeft + GRID_CELLS * CELL_SIZE
                && mouseY < gridTop + GRID_CELLS * CELL_SIZE;
    }

    private int hoveredChunkX(double mouseX) {
        return originChunkX + (int) ((mouseX - gridLeft) / CELL_SIZE) - GRID_CELLS / 2;
    }

    private int hoveredChunkZ(double mouseY) {
        return originChunkZ + (int) ((mouseY - gridTop) / CELL_SIZE) - GRID_CELLS / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
