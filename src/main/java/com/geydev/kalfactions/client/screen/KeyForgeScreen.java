package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.KeyForgeBlockEntity;
import com.geydev.kalfactions.block.KeyForgeType;
import com.geydev.kalfactions.menu.KeyForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class KeyForgeScreen extends AbstractContainerScreen<KeyForgeMenu> {
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 190;
    public static final int GHOST_PANEL_HEIGHT = 202;
    public static final int SCULK_PROGRESS_X = 48;
    public static final int SCULK_PROGRESS_Y = 46;
    public static final int SCULK_PROGRESS_WIDTH = 80;
    public static final int SCULK_PROGRESS_HEIGHT = 29;
    public static final int SCULK_PROGRESS_TEXTURE_HEIGHT = 58;
    public static final int GHOST_PROGRESS_X = 66;
    public static final int GHOST_PROGRESS_Y = 43;
    public static final int GHOST_PROGRESS_WIDTH = 43;
    public static final int GHOST_PROGRESS_HEIGHT = 43;
    public static final int GHOST_OVERLAY_WIDTH = 176;
    public static final int GHOST_OVERLAY_HEIGHT = 106;
    public static final int GHOST_IDLE_FRAMES = 8;
    public static final int GHOST_READY_FRAMES = 6;
    public static final int GHOST_PROGRESS_FRAMES = 24;
    public static final int GHOST_COMPLETE_FRAMES = 10;
    public static final int GHOST_IDLE_COLUMNS = 4;
    public static final int GHOST_READY_COLUMNS = 3;
    public static final int GHOST_PROGRESS_COLUMNS = 6;
    public static final int GHOST_COMPLETE_COLUMNS = 5;
    public static final int INFERNAL_PROGRESS_X = 42;
    public static final int INFERNAL_PROGRESS_Y = 46;
    public static final int INFERNAL_PROGRESS_WIDTH = 92;
    public static final int INFERNAL_PROGRESS_HEIGHT = 30;
    public static final int MOSSY_PROGRESS_X = 43;
    public static final int MOSSY_PROGRESS_Y = 53;
    public static final int MOSSY_PROGRESS_WIDTH = 90;
    public static final int MOSSY_PROGRESS_HEIGHT = 15;

    private static final int INFERNAL_BRANCH_END = 35;
    private static final int INFERNAL_CHAMBER_END = 55;
    private static final int INFERNAL_CHANNEL_END = 90;
    private static final int INFERNAL_BRANCH_HEIGHT = 14;
    private static final int INFERNAL_CHAMBER_X = 15;
    private static final int INFERNAL_CHAMBER_Y = 10;
    private static final int INFERNAL_CHAMBER_WIDTH = 13;
    private static final int INFERNAL_CHAMBER_HEIGHT = 13;
    private static final int INFERNAL_CHANNEL_X = 27;
    private static final int INFERNAL_CHANNEL_Y = 16;
    private static final int INFERNAL_CHANNEL_WIDTH = 20;
    private static final int INFERNAL_CHANNEL_HEIGHT = 4;
    private static final int INFERNAL_SPOUT_X = 43;
    private static final int INFERNAL_SPOUT_Y = 20;
    private static final int INFERNAL_SPOUT_WIDTH = 5;
    private static final int INFERNAL_SPOUT_HEIGHT = 10;
    private static final int INFERNAL_HIGHLIGHT_X = 91;
    private static final int GHOST_IDLE_ROWS = 2;
    private static final int GHOST_READY_ROWS = 2;
    private static final int GHOST_PROGRESS_ROWS = 4;
    private static final int GHOST_COMPLETE_ROWS = 2;
    private static final int GHOST_COMPLETE_DURATION_TICKS = 14;
    private static final ResourceLocation GHOST_IDLE_TEXTURE = ghostTexture("ghost_key_forge_idle.png");
    private static final ResourceLocation GHOST_READY_TEXTURE = ghostTexture("ghost_key_forge_ready.png");
    private static final ResourceLocation GHOST_PROGRESS_TEXTURE = ghostTexture("ghost_key_forge_progress.png");
    private static final ResourceLocation GHOST_COMPLETE_TEXTURE = ghostTexture("ghost_key_forge_complete.png");

    private static final ProgressPiece[][] SCULK_STAGES = {
            {piece(2, 0, 3, 2), piece(38, 0, 3, 2), piece(74, 0, 3, 2)},
            {piece(5, 3, 3, 2), piece(38, 3, 3, 2), piece(71, 3, 3, 2)},
            {piece(10, 6, 3, 2), piece(38, 6, 3, 2), piece(66, 6, 3, 2)},
            {piece(16, 9, 4, 2), piece(38, 9, 3, 2), piece(60, 9, 4, 2)},
            {piece(23, 11, 4, 2), piece(38, 12, 3, 2), piece(53, 11, 4, 2)},
            {piece(30, 13, 4, 2), piece(38, 15, 3, 2), piece(46, 13, 4, 2)},
            {piece(35, 16, 4, 2), piece(41, 16, 4, 2)},
            {piece(38, 18, 4, 2)},
            {piece(38, 21, 4, 2)},
            {piece(38, 24, 4, 2)},
            {piece(37, 27, 6, 2)}
    };

    private final ResourceLocation background;
    private final ResourceLocation progressTexture;
    private int ghostCompleteTick = -1;
    private boolean ghostObservedAssembly;
    private boolean ghostResultPresent;

    public KeyForgeScreen(KeyForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = menu.forgeType() == KeyForgeType.GHOST ? GHOST_PANEL_HEIGHT : PANEL_HEIGHT;
        titleLabelX = menu.forgeType() == KeyForgeType.GHOST ? 15 : 8;
        titleLabelY = 7;
        inventoryLabelX = menu.forgeType() == KeyForgeType.GHOST
                ? 15
                : KeyForgeMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = menu.forgeType() == KeyForgeType.GHOST ? 107 : 97;
        String path = menu.forgeType().serializedName();
        background = ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                menu.forgeType() == KeyForgeType.GHOST
                        ? "textures/gui/key_forge/ghost_key_forge.png"
                        : "textures/gui/" + path + "/" + path + ".png"
        );
        progressTexture = ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                "textures/gui/" + path + "/progress.png"
        );
    }

    @Override
    protected void init() {
        super.init();
        if (menu.forgeType() == KeyForgeType.GHOST) {
            ghostResultPresent = menu.getSlot(KeyForgeBlockEntity.RESULT_SLOT).hasItem();
            ghostObservedAssembly = menu.progress() > 0;
            ghostCompleteTick = -1;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.forgeType() != KeyForgeType.GHOST) {
            return;
        }
        if (ghostCompleteTick >= 0 && ++ghostCompleteTick >= GHOST_COMPLETE_DURATION_TICKS) {
            ghostCompleteTick = -1;
        }
        boolean hasResult = menu.getSlot(KeyForgeBlockEntity.RESULT_SLOT).hasItem();
        boolean hasInput = menu.hasCompleteInput();
        if (!hasResult && (menu.progress() > 0 || hasInput)) {
            ghostObservedAssembly = true;
        }
        if (hasResult && !ghostResultPresent && ghostObservedAssembly) {
            ghostCompleteTick = 0;
            ghostObservedAssembly = false;
        }
        if (!hasResult && menu.progress() == 0 && !hasInput) {
            ghostObservedAssembly = false;
        }
        ghostResultPresent = hasResult;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (insideProgress(mouseX, mouseY)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "screen.kingdoms.key_forge.progress",
                            Math.round(menu.progressFraction() * 100.0F),
                            menu.remainingTicks()
                    ),
                    mouseX,
                    mouseY
            );
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                background,
                leftPos,
                topPos,
                0.0F,
                0.0F,
                PANEL_WIDTH,
                imageHeight,
                PANEL_WIDTH,
                imageHeight
        );
        switch (menu.forgeType()) {
            case GHOST -> renderGhostProgress(graphics);
            case SCULK -> renderSculkProgress(graphics);
            case INFERNAL -> renderInfernalProgress(graphics);
            case MOSSY -> renderMossyProgress(graphics);
        }
    }

    private void renderMossyProgress(GuiGraphics graphics) {
        int filled = segmentPixels(menu.progress(), 0, menu.totalTicks(), MOSSY_PROGRESS_WIDTH);
        if (filled > 0) {
            graphics.blit(
                    progressTexture,
                    leftPos + MOSSY_PROGRESS_X,
                    topPos + MOSSY_PROGRESS_Y,
                    0.0F,
                    0.0F,
                    filled,
                    MOSSY_PROGRESS_HEIGHT,
                    MOSSY_PROGRESS_WIDTH,
                    MOSSY_PROGRESS_HEIGHT
            );
        }
    }

    private void renderGhostProgress(GuiGraphics graphics) {
        if (ghostCompleteTick >= 0) {
            int frame = Math.min(
                    GHOST_COMPLETE_FRAMES - 1,
                    ghostCompleteTick * GHOST_COMPLETE_FRAMES / GHOST_COMPLETE_DURATION_TICKS
            );
            renderGhostFrame(
                    graphics,
                    GHOST_COMPLETE_TEXTURE,
                    frame,
                    GHOST_COMPLETE_COLUMNS,
                    GHOST_COMPLETE_ROWS
            );
            return;
        }
        boolean hasResult = menu.getSlot(KeyForgeBlockEntity.RESULT_SLOT).hasItem();
        if (menu.progress() > 0) {
            renderGhostFrame(
                    graphics,
                    GHOST_PROGRESS_TEXTURE,
                    ghostProgressFrame(menu.progressFraction()),
                    GHOST_PROGRESS_COLUMNS,
                    GHOST_PROGRESS_ROWS
            );
            return;
        }
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        if (!hasResult && menu.hasCompleteInput()) {
            renderGhostFrame(
                    graphics,
                    GHOST_READY_TEXTURE,
                    (int) (gameTime / 2L % GHOST_READY_FRAMES),
                    GHOST_READY_COLUMNS,
                    GHOST_READY_ROWS
            );
            return;
        }
        renderGhostFrame(
                graphics,
                GHOST_IDLE_TEXTURE,
                (int) (gameTime / 3L % GHOST_IDLE_FRAMES),
                GHOST_IDLE_COLUMNS,
                GHOST_IDLE_ROWS
        );
    }

    private void renderGhostFrame(
            GuiGraphics graphics,
            ResourceLocation texture,
            int frame,
            int columns,
            int rows
    ) {
        int textureX = frame % columns * GHOST_OVERLAY_WIDTH;
        int textureY = frame / columns * GHOST_OVERLAY_HEIGHT;
        graphics.blit(
                texture,
                leftPos,
                topPos,
                textureX,
                textureY,
                GHOST_OVERLAY_WIDTH,
                GHOST_OVERLAY_HEIGHT,
                columns * GHOST_OVERLAY_WIDTH,
                rows * GHOST_OVERLAY_HEIGHT
        );
    }

    private void renderSculkProgress(GuiGraphics graphics) {
        int progress = menu.progress();
        if (progress <= 0) {
            return;
        }
        int litStages = Math.min(
                SCULK_STAGES.length,
                Math.max(1, (progress * SCULK_STAGES.length + menu.totalTicks() - 1) / menu.totalTicks())
        );
        for (int stage = 0; stage < litStages; stage++) {
            renderStage(graphics, SCULK_STAGES[stage], 0);
        }
        renderStage(graphics, SCULK_STAGES[litStages - 1], SCULK_PROGRESS_HEIGHT);
    }

    private void renderInfernalProgress(GuiGraphics graphics) {
        int progress = menu.progress();
        int branchRows = segmentPixels(progress, 0, INFERNAL_BRANCH_END, INFERNAL_BRANCH_HEIGHT);
        if (branchRows > 0) {
            blitInfernal(graphics, 0, 0, INFERNAL_PROGRESS_WIDTH, branchRows);
        }
        int chamberRows = segmentPixels(
                progress,
                INFERNAL_BRANCH_END,
                INFERNAL_CHAMBER_END,
                INFERNAL_CHAMBER_HEIGHT
        );
        if (chamberRows > 0) {
            blitInfernal(
                    graphics,
                    INFERNAL_CHAMBER_X,
                    INFERNAL_CHAMBER_Y,
                    INFERNAL_CHAMBER_WIDTH,
                    chamberRows
            );
        }
        int channelPixels = segmentPixels(
                progress,
                INFERNAL_CHAMBER_END,
                INFERNAL_CHANNEL_END,
                INFERNAL_CHANNEL_WIDTH
        );
        if (channelPixels > 0) {
            blitInfernal(
                    graphics,
                    INFERNAL_CHANNEL_X,
                    INFERNAL_CHANNEL_Y,
                    channelPixels,
                    INFERNAL_CHANNEL_HEIGHT
            );
            graphics.blit(
                    progressTexture,
                    leftPos + INFERNAL_PROGRESS_X + INFERNAL_CHANNEL_X + channelPixels - 1,
                    topPos + INFERNAL_PROGRESS_Y + INFERNAL_CHANNEL_Y,
                    INFERNAL_HIGHLIGHT_X,
                    INFERNAL_CHANNEL_Y,
                    1,
                    INFERNAL_CHANNEL_HEIGHT,
                    INFERNAL_PROGRESS_WIDTH,
                    INFERNAL_PROGRESS_HEIGHT
            );
        }
        int spoutRows = segmentPixels(
                progress,
                INFERNAL_CHANNEL_END,
                menu.totalTicks(),
                INFERNAL_SPOUT_HEIGHT
        );
        if (spoutRows > 0) {
            blitInfernal(
                    graphics,
                    INFERNAL_SPOUT_X,
                    INFERNAL_SPOUT_Y,
                    INFERNAL_SPOUT_WIDTH,
                    spoutRows
            );
        }
    }

    private void blitInfernal(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.blit(
                progressTexture,
                leftPos + INFERNAL_PROGRESS_X + x,
                topPos + INFERNAL_PROGRESS_Y + y,
                x,
                y,
                width,
                height,
                INFERNAL_PROGRESS_WIDTH,
                INFERNAL_PROGRESS_HEIGHT
        );
    }

    static int segmentPixels(int progress, int startTick, int endTick, int pixelLength) {
        if (progress <= startTick || endTick <= startTick || pixelLength <= 0) {
            return 0;
        }
        int elapsed = Math.min(progress, endTick) - startTick;
        return Math.clamp(
                Math.round(elapsed * pixelLength / (float) (endTick - startTick)),
                1,
                pixelLength
        );
    }

    static int ghostProgressFrame(float progressFraction) {
        return Math.clamp(
                Math.round(progressFraction * (GHOST_PROGRESS_FRAMES - 1)),
                0,
                GHOST_PROGRESS_FRAMES - 1
        );
    }

    private void renderStage(GuiGraphics graphics, ProgressPiece[] pieces, int textureY) {
        for (ProgressPiece piece : pieces) {
            graphics.blit(
                    progressTexture,
                    leftPos + SCULK_PROGRESS_X + piece.x(),
                    topPos + SCULK_PROGRESS_Y + piece.y(),
                    piece.x(),
                    textureY + piece.y(),
                    piece.width(),
                    piece.height(),
                    SCULK_PROGRESS_WIDTH,
                    SCULK_PROGRESS_TEXTURE_HEIGHT
            );
        }
    }

    private boolean insideProgress(int mouseX, int mouseY) {
        int x = switch (menu.forgeType()) {
            case GHOST -> GHOST_PROGRESS_X;
            case SCULK -> SCULK_PROGRESS_X;
            case INFERNAL -> INFERNAL_PROGRESS_X;
            case MOSSY -> MOSSY_PROGRESS_X;
        };
        int y = switch (menu.forgeType()) {
            case GHOST -> GHOST_PROGRESS_Y;
            case SCULK -> SCULK_PROGRESS_Y;
            case INFERNAL -> INFERNAL_PROGRESS_Y;
            case MOSSY -> MOSSY_PROGRESS_Y;
        };
        int width = switch (menu.forgeType()) {
            case GHOST -> GHOST_PROGRESS_WIDTH;
            case SCULK -> SCULK_PROGRESS_WIDTH;
            case INFERNAL -> INFERNAL_PROGRESS_WIDTH;
            case MOSSY -> MOSSY_PROGRESS_WIDTH;
        };
        int height = switch (menu.forgeType()) {
            case GHOST -> GHOST_PROGRESS_HEIGHT;
            case SCULK -> SCULK_PROGRESS_HEIGHT;
            case INFERNAL -> INFERNAL_PROGRESS_HEIGHT;
            case MOSSY -> MOSSY_PROGRESS_HEIGHT;
        };
        return mouseX >= leftPos + x
                && mouseX < leftPos + x + width
                && mouseY >= topPos + y
                && mouseY < topPos + y + height;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int text = switch (menu.forgeType()) {
            case GHOST -> 0xFFF0FDFF;
            case SCULK -> 0xFFBDEFF1;
            case INFERNAL -> 0xFFE4C7AF;
            case MOSSY -> 0xFFE9E1B9;
        };
        int muted = switch (menu.forgeType()) {
            case GHOST -> 0xFFC0D4E2;
            case SCULK -> 0xFF76969A;
            case INFERNAL -> 0xFF9F8172;
            case MOSSY -> 0xFF88916D;
        };
        boolean shadow = menu.forgeType() == KeyForgeType.GHOST;
        graphics.drawString(font, title, titleLabelX, titleLabelY, text, shadow);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, muted, shadow);
    }

    private static ProgressPiece piece(int x, int y, int width, int height) {
        return new ProgressPiece(x, y, width, height);
    }

    private static ResourceLocation ghostTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                KalFactions.MOD_ID,
                "textures/gui/key_forge/" + name
        );
    }

    private record ProgressPiece(int x, int y, int width, int height) {
    }
}
