package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.menu.DrillMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class DrillScreen extends AbstractContainerScreen<DrillMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/drill/drill_gui.png");
    private static final ResourceLocation PROGRESS =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/drill/progress.png");
    private static final int BACKGROUND_WIDTH = 370;
    private static final int BACKGROUND_HEIGHT = 188;
    private static final int PROGRESS_WIDTH = 162;
    private static final int PROGRESS_HEIGHT = 20;

    public DrillScreen(DrillMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = BACKGROUND_WIDTH;
        imageHeight = BACKGROUND_HEIGHT;
        inventoryLabelX = 0;
        inventoryLabelY = 0;
        titleLabelX = 0;
        titleLabelY = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, imageWidth, imageHeight);
        int width = Math.max(0, (int) (PROGRESS_WIDTH * menu.progressFraction()));
        if (width > 0) {
            graphics.blit(
                    PROGRESS,
                    leftPos + 114,
                    topPos + 30,
                    width,
                    PROGRESS_HEIGHT,
                    0.0F,
                    0.0F,
                    width,
                    PROGRESS_HEIGHT,
                    PROGRESS_WIDTH,
                    PROGRESS_HEIGHT
            );
        }
        Component timer = Component.translatable("screen.kingdoms.drill.remaining", formatTicks(menu.remainingTicks()));
        graphics.drawString(
                font,
                timer,
                leftPos + 230 - font.width(timer) / 2,
                topPos + 66,
                0xFFE8D6A0,
                true
        );
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private static String formatTicks(int ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
