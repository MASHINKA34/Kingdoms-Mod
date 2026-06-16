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

    public DrillScreen(DrillMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 228;
        inventoryLabelX = 48;
        inventoryLabelY = 128;
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
        int progressWidth = 112;
        int width = Math.max(0, (int) (progressWidth * menu.progressFraction()));
        if (width > 0) {
            graphics.blit(PROGRESS, leftPos + 110, topPos + 49, width, 10, 0.0F, 0.0F, width, 16, 128, 16);
        }
        Component timer = Component.translatable("screen.kingdoms.drill.remaining", formatTicks(menu.remainingTicks()));
        graphics.drawString(
                font,
                timer,
                leftPos + 166 - font.width(timer) / 2,
                topPos + 64,
                0xFFE8D6A0,
                true
        );
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 19, 0xFFFFE8AA, true);
        graphics.drawString(font, Component.translatable("screen.kingdoms.drill.storage"), 42, 77, 0xFFE8D6A0, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE8D6A0, true);
    }

    private static String formatTicks(int ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
