package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.menu.ResearchBenchMenu;
import com.geydev.kalfactions.science.ResearchBenchStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ResearchBenchScreen extends AbstractContainerScreen<ResearchBenchMenu> {
    private static final int PANEL_WIDTH = 226;
    private static final int PANEL_HEIGHT = 210;
    private static final int READOUT_X = 92;
    private static final int READOUT_Y = 34;
    private static final int READOUT_WIDTH = 126;
    private static final int READOUT_HEIGHT = 78;
    private static final int PROGRESS_X = 98;
    private static final int PROGRESS_Y = 44;
    private static final int PROGRESS_WIDTH = 114;
    private static final int PROGRESS_HEIGHT = 10;
    private static final int INVENTORY_PANEL_TOP = 112;
    private static final int INVENTORY_LABEL_Y = 112;
    private static final int LINE_STEP = 12;
    private static final int GOLD = 0xFFE8D6A0;
    private static final int TEXT = 0xFFB9C8D5;
    private static final int SCIENCE = 0xFF8FD8F5;
    private static final int WORKING = 0xFF9BE07A;
    private static final int WARNING = 0xFFF0D99D;
    private static final int DANGER = 0xFFE08A7A;

    public ResearchBenchScreen(ResearchBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelX = 10;
        titleLabelY = 10;
        inventoryLabelX = ResearchBenchMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderProgressTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF101820);
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xFF172331);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xFF0D141D);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFFB98E35);
        graphics.fill(leftPos, topPos + imageHeight - 2, leftPos + imageWidth, topPos + imageHeight, 0xFF4D3210);
        renderReadoutPlate(graphics);
        renderProgressBar(graphics);
        renderInputSlots(graphics);
        renderPlayerInventoryPanel(graphics);
    }

    private void renderReadoutPlate(GuiGraphics graphics) {
        int left = leftPos + READOUT_X;
        int top = topPos + READOUT_Y;
        graphics.fill(left, top, left + READOUT_WIDTH, top + READOUT_HEIGHT, 0xFF080B10);
        graphics.fill(left + 1, top + 1, left + READOUT_WIDTH - 1, top + READOUT_HEIGHT - 1, 0xFF17222F);
        graphics.fill(left, top, left + READOUT_WIDTH, top + 2, 0xFFC6A24C);
    }

    private void renderProgressBar(GuiGraphics graphics) {
        int left = leftPos + PROGRESS_X;
        int top = topPos + PROGRESS_Y;
        graphics.fill(left - 1, top - 1, left + PROGRESS_WIDTH + 1, top + PROGRESS_HEIGHT + 1, 0xFF5D3B13);
        graphics.fill(left, top, left + PROGRESS_WIDTH, top + PROGRESS_HEIGHT, 0xFF0A1119);
        int filled = Math.round(PROGRESS_WIDTH * menu.progressFraction());
        if (filled <= 0) {
            return;
        }
        int color = menu.status() == ResearchBenchStatus.WORKING ? 0xFF2F7FA8 : 0xFF6A5A2A;
        int highlight = menu.status() == ResearchBenchStatus.WORKING ? 0xFF5AC8F0 : 0xFFC6A24C;
        graphics.fill(left, top, left + filled, top + PROGRESS_HEIGHT, color);
        graphics.fill(left, top, left + filled, top + 2, highlight);
    }

    private void renderInputSlots(GuiGraphics graphics) {
        for (int slot = 0; slot < ResearchBenchMenu.SLOTS; slot++) {
            int row = slot / ResearchBenchMenu.INPUT_COLUMNS;
            int column = slot % ResearchBenchMenu.INPUT_COLUMNS;
            goldSlotFrame(
                    graphics,
                    leftPos + ResearchBenchMenu.INPUT_SLOT_X + column * ResearchBenchMenu.INPUT_SLOT_STEP,
                    topPos + ResearchBenchMenu.INPUT_SLOT_Y + row * ResearchBenchMenu.INPUT_SLOT_STEP
            );
        }
    }

    private void renderPlayerInventoryPanel(GuiGraphics graphics) {
        int panelTop = topPos + INVENTORY_PANEL_TOP;
        graphics.fill(leftPos + 6, panelTop, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xFF101820);
        graphics.fill(leftPos + 6, panelTop, leftPos + imageWidth - 6, panelTop + 1, 0xFFB98E35);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                inventorySlotFrame(
                        graphics,
                        leftPos + ResearchBenchMenu.PLAYER_INVENTORY_X + column * 18,
                        topPos + ResearchBenchMenu.PLAYER_INVENTORY_Y + row * 18
                );
            }
        }
        for (int column = 0; column < 9; column++) {
            inventorySlotFrame(
                    graphics,
                    leftPos + ResearchBenchMenu.PLAYER_INVENTORY_X + column * 18,
                    topPos + ResearchBenchMenu.PLAYER_HOTBAR_Y
            );
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GOLD, false);
        int line = READOUT_Y + 26;
        graphics.drawString(font, statusLine(), READOUT_X + 6, line, statusColor(), false);
        line += LINE_STEP;
        graphics.drawString(font, scienceTodayLine(), READOUT_X + 6, line, SCIENCE, false);
        line += LINE_STEP;
        graphics.drawString(font, speedLine(), READOUT_X + 6, line, TEXT, false);
        line += LINE_STEP;
        if (menu.currentScience() > 0) {
            graphics.drawString(font, materialLine(), READOUT_X + 6, line, TEXT, false);
        }
    }

    private Component statusLine() {
        return switch (menu.status()) {
            case WORKING -> Component.translatable("screen.kingdoms.research_bench.status.working");
            case DAILY_CAP -> Component.translatable("screen.kingdoms.research_bench.status.daily_cap");
            case OFF_TERRITORY -> Component.translatable("screen.kingdoms.research_bench.status.off_territory");
            case NO_MATERIALS -> Component.translatable("screen.kingdoms.research_bench.status.no_materials");
        };
    }

    private int statusColor() {
        return switch (menu.status()) {
            case WORKING -> WORKING;
            case DAILY_CAP -> WARNING;
            case OFF_TERRITORY -> DANGER;
            case NO_MATERIALS -> TEXT;
        };
    }

    private Component scienceTodayLine() {
        return menu.capped()
                ? Component.translatable("screen.kingdoms.science_today", menu.scienceToday(), menu.dailyCap())
                : Component.translatable("screen.kingdoms.science_today_unlimited", menu.scienceToday());
    }

    private Component speedLine() {
        return Component.translatable(
                "screen.kingdoms.research_bench.speed",
                Math.max(1, menu.intervalTicks() / 20)
        );
    }

    private Component materialLine() {
        return Component.translatable("screen.kingdoms.research_bench.material", menu.currentScience());
    }

    private void renderProgressTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (mouseX < leftPos + PROGRESS_X
                || mouseX >= leftPos + PROGRESS_X + PROGRESS_WIDTH
                || mouseY < topPos + PROGRESS_Y
                || mouseY >= topPos + PROGRESS_Y + PROGRESS_HEIGHT) {
            return;
        }
        Component tooltip = menu.status() == ResearchBenchStatus.WORKING
                ? Component.translatable(
                        "screen.kingdoms.research_bench.remaining",
                        formatTicks(menu.remainingTicks()))
                : statusLine();
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private static String formatTicks(int ticks) {
        long totalSeconds = Math.max(0L, ticks / 20L);
        return String.format(
                "%02d:%02d:%02d",
                totalSeconds / 3600L,
                totalSeconds % 3600L / 60L,
                totalSeconds % 60L
        );
    }

    private static void goldSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 16, y + 16, 0xE40A1119);
        graphics.fill(x - 2, y - 2, x + 18, y - 1, 0xFFE0B857);
        graphics.fill(x - 2, y + 17, x + 18, y + 18, 0xFF5D3B13);
        graphics.fill(x - 2, y - 1, x - 1, y + 17, 0xFFB8872F);
        graphics.fill(x + 17, y - 1, x + 18, y + 17, 0xFF6D4919);
    }

    private static void inventorySlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF05080D);
        graphics.fill(x, y, x + 16, y + 16, 0xFF24303D);
        graphics.fill(x, y, x + 16, y + 1, 0xFF546373);
        graphics.fill(x, y, x + 1, y + 16, 0xFF3C4855);
        graphics.fill(x, y + 15, x + 16, y + 16, 0xFF121923);
        graphics.fill(x + 15, y, x + 16, y + 16, 0xFF121923);
    }
}
