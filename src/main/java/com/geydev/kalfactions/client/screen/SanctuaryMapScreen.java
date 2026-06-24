package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SanctuaryMapScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CELL_SIZE = 9;
    private static final int GOLD = 0xFFF3D58B;
    private static final int TEXT = 0xFFEDE6D6;
    private static final int MUTED = 0xFFB6AA92;
    private static final int SANCTUARY_FILL = 0xFF32D6C8;
    private static final long STATUS_DURATION_MILLIS = 3200L;

    private final BlockPos corePos;
    private final Set<Long> chunks = new HashSet<>();
    private int centerChunkX;
    private int centerChunkZ;
    private int radius;
    private String statusMessage = "";
    private boolean statusSuccessful;
    private long statusShownAt;

    private int panelLeft;
    private int panelTop;
    private int gridLeft;
    private int gridTop;

    public SanctuaryMapScreen(FactionPayloads.S2COpenSanctuary payload) {
        super(Component.translatable("screen.kingdoms.sanctuary.title"));
        this.corePos = payload.corePos();
        acceptState(payload);
    }

    public BlockPos corePos() {
        return corePos;
    }

    public void acceptState(FactionPayloads.S2COpenSanctuary payload) {
        centerChunkX = payload.centerChunkX();
        centerChunkZ = payload.centerChunkZ();
        radius = payload.radius();
        chunks.clear();
        chunks.addAll(payload.chunks());
        String message = payload.message().getString();
        if (!message.isBlank()) {
            statusMessage = message;
            statusSuccessful = payload.successful();
            statusShownAt = System.currentTimeMillis();
        }
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        gridLeft = panelLeft + 18;
        gridTop = panelTop + 40;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                panelLeft + PANEL_WIDTH - 78,
                panelTop + PANEL_HEIGHT - 26,
                66,
                20
        ));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 12, GOLD, true);

        int gridSize = radius * 2 + 1;
        for (int row = 0; row < gridSize; row++) {
            for (int column = 0; column < gridSize; column++) {
                int chunkX = centerChunkX + column - radius;
                int chunkZ = centerChunkZ + row - radius;
                int x = gridLeft + column * CELL_SIZE;
                int y = gridTop + row * CELL_SIZE;
                int parchment = ((chunkX * 31 + chunkZ * 17) & 1) == 0 ? 0xFFBFA66F : 0xFFC9B47E;
                graphics.fill(x, y, x + CELL_SIZE - 1, y + CELL_SIZE - 1, parchment);
                if (isSanctuary(chunkX, chunkZ)) {
                    graphics.fill(x + 1, y + 1, x + CELL_SIZE - 2, y + CELL_SIZE - 2, SANCTUARY_FILL);
                }
                if (chunkX == centerChunkX && chunkZ == centerChunkZ) {
                    graphics.fill(x + 3, y + 3, x + CELL_SIZE - 4, y + CELL_SIZE - 4, 0xFFFFFFFF);
                }
            }
        }

        int infoX = panelLeft + 190;
        if (insideGrid(mouseX, mouseY)) {
            int chunkX = hoveredChunkX(mouseX);
            int chunkZ = hoveredChunkZ(mouseY);
            Component label = isSanctuary(chunkX, chunkZ)
                    ? Component.translatable("screen.kingdoms.sanctuary.chunk_marked", chunkX, chunkZ)
                    : Component.translatable("screen.kingdoms.sanctuary.chunk", chunkX, chunkZ);
            graphics.drawString(font, label, infoX, panelTop + 44, TEXT, false);
        }
        graphics.drawWordWrap(
                font,
                Component.translatable("screen.kingdoms.sanctuary.help"),
                infoX,
                panelTop + 60,
                PANEL_WIDTH - 190 - 16,
                MUTED
        );
        renderStatus(graphics);
    }

    private void renderStatus(GuiGraphics graphics) {
        if (statusMessage.isBlank() || System.currentTimeMillis() - statusShownAt > STATUS_DURATION_MILLIS) {
            return;
        }
        int color = statusSuccessful ? 0xFF91D69B : 0xFFE29388;
        graphics.drawString(
                font,
                font.plainSubstrByWidth(statusMessage, PANEL_WIDTH - 24),
                panelLeft + 18,
                panelTop + PANEL_HEIGHT - 22,
                color,
                true
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideGrid(mouseX, mouseY)) {
            int chunkX = hoveredChunkX(mouseX);
            int chunkZ = hoveredChunkZ(mouseY);
            boolean claimed = !isSanctuary(chunkX, chunkZ);
            PacketDistributor.sendToServer(new FactionPayloads.C2SSanctuarySetClaim(
                    corePos,
                    chunkX,
                    chunkZ,
                    claimed
            ));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isSanctuary(int chunkX, int chunkZ) {
        return chunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        int gridSize = radius * 2 + 1;
        return mouseX >= gridLeft
                && mouseY >= gridTop
                && mouseX < gridLeft + gridSize * CELL_SIZE
                && mouseY < gridTop + gridSize * CELL_SIZE;
    }

    private int hoveredChunkX(double mouseX) {
        return centerChunkX + (int) ((mouseX - gridLeft) / CELL_SIZE) - radius;
    }

    private int hoveredChunkZ(double mouseY) {
        return centerChunkZ + (int) ((mouseY - gridTop) / CELL_SIZE) - radius;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
