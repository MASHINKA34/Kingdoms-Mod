package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.menu.ResearchBenchMenu;
import com.geydev.kalfactions.science.ResearchBenchStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

public final class ResearchBenchScreen extends AbstractContainerScreen<ResearchBenchMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "kingdoms",
            "textures/gui/research_bench/research_bench.png"
    );
    private static final ResourceLocation SLOT_STATES = ResourceLocation.fromNamespaceAndPath(
            "kingdoms",
            "textures/gui/research_bench/slot_states.png"
    );
    private static final ResourceLocation PROGRESS = ResourceLocation.fromNamespaceAndPath(
            "kingdoms",
            "textures/gui/research_bench/progress.png"
    );
    private static final int PANEL_WIDTH = 276;
    private static final int PANEL_HEIGHT = 236;
    private static final int READOUT_X = 97;
    private static final int READOUT_WIDTH = 166;
    private static final int PROGRESS_X = 99;
    private static final int PROGRESS_Y = 44;
    private static final int PROGRESS_WIDTH = 162;
    private static final int PROGRESS_HEIGHT = 10;
    private static final int TEXT_TOP = 64;
    private static final int INVENTORY_LABEL_Y = 134;
    private static final int LINE_STEP = 10;
    private static final int INPUT_SLOT_SIZE = 20;
    private static final int INPUT_SLOT_ATLAS_WIDTH = 100;
    private static final int SLOT_ATLAS_HEIGHT = 40;
    private static final int PLAYER_SLOT_SIZE = 18;
    private static final int PLAYER_SLOT_V = 22;
    private static final int PROGRESS_TEXTURE_WIDTH = 164;
    private static final int PROGRESS_TEXTURE_HEIGHT = 20;
    private static final int PROGRESS_IDLE_V = 10;
    private static final int GOLD = 0xFFE5BE68;
    private static final int PARCHMENT_TEXT = 0xFF3D2A22;
    private static final int SCIENCE = 0xFF245D70;
    private static final int WORKING = 0xFF356B3C;
    private static final int WARNING = 0xFF76551D;
    private static final int DANGER = 0xFF873B31;

    public ResearchBenchScreen(ResearchBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelX = 14;
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
        graphics.blit(BACKGROUND, leftPos, topPos, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
        renderProgressBar(graphics);
        renderInputSlots(graphics, mouseX, mouseY);
        renderPlayerSlots(graphics, mouseX, mouseY);
    }

    private void renderProgressBar(GuiGraphics graphics) {
        int filled = Math.round(PROGRESS_WIDTH * menu.progressFraction());
        if (filled <= 0) {
            return;
        }
        int sourceY = menu.status() == ResearchBenchStatus.WORKING ? 0 : PROGRESS_IDLE_V;
        graphics.blit(
                PROGRESS,
                leftPos + PROGRESS_X,
                topPos + PROGRESS_Y,
                0.0F,
                sourceY,
                filled,
                PROGRESS_HEIGHT,
                PROGRESS_TEXTURE_WIDTH,
                PROGRESS_TEXTURE_HEIGHT
        );
    }

    private void renderInputSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        int activeSlot = activeInputSlot();
        boolean locked = menu.status() == ResearchBenchStatus.OFF_TERRITORY
                || menu.status() == ResearchBenchStatus.DAILY_CAP;
        for (int slot = 0; slot < ResearchBenchMenu.SLOTS; slot++) {
            int row = slot / ResearchBenchMenu.INPUT_COLUMNS;
            int column = slot % ResearchBenchMenu.INPUT_COLUMNS;
            int itemX = ResearchBenchMenu.INPUT_SLOT_X + column * ResearchBenchMenu.INPUT_SLOT_STEP;
            int itemY = ResearchBenchMenu.INPUT_SLOT_Y + row * ResearchBenchMenu.INPUT_SLOT_STEP;
            boolean hovered = inside(mouseX, mouseY, itemX - 2, itemY - 2, INPUT_SLOT_SIZE);
            boolean filled = menu.slots.get(slot).hasItem();
            int state = locked ? 3 : slot == activeSlot ? 2 : hovered ? 1 : filled ? 4 : 0;
            graphics.blit(
                    SLOT_STATES,
                    leftPos + itemX - 2,
                    topPos + itemY - 2,
                    state * INPUT_SLOT_SIZE,
                    0.0F,
                    INPUT_SLOT_SIZE,
                    INPUT_SLOT_SIZE,
                    INPUT_SLOT_ATLAS_WIDTH,
                    SLOT_ATLAS_HEIGHT
            );
        }
    }

    private void renderPlayerSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = ResearchBenchMenu.SLOTS; index < menu.slots.size(); index++) {
            var slot = menu.slots.get(index);
            boolean hovered = inside(mouseX, mouseY, slot.x - 1, slot.y - 1, PLAYER_SLOT_SIZE);
            int state = hovered ? 1 : slot.hasItem() ? 2 : 0;
            graphics.blit(
                    SLOT_STATES,
                    leftPos + slot.x - 1,
                    topPos + slot.y - 1,
                    state * PLAYER_SLOT_SIZE,
                    PLAYER_SLOT_V,
                    PLAYER_SLOT_SIZE,
                    PLAYER_SLOT_SIZE,
                    INPUT_SLOT_ATLAS_WIDTH,
                    SLOT_ATLAS_HEIGHT
            );
        }
    }

    private int activeInputSlot() {
        if (menu.status() != ResearchBenchStatus.WORKING) {
            return -1;
        }
        for (int slot = 0; slot < ResearchBenchMenu.SLOTS; slot++) {
            if (menu.slots.get(slot).hasItem()) {
                return slot;
            }
        }
        return -1;
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int size) {
        int left = leftPos + x;
        int top = topPos + y;
        return mouseX >= left && mouseX < left + size && mouseY >= top && mouseY < top + size;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.research_bench.components"),
                18,
                31,
                GOLD,
                false
        );
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.research_bench.research"),
                101,
                31,
                PARCHMENT_TEXT,
                false
        );
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GOLD, false);
        int line = drawWrapped(graphics, factionLine(), TEXT_TOP, statusColor());
        line = drawWrapped(graphics, scienceTodayLine(), line, SCIENCE);
        line = drawWrapped(graphics, speedLine(), line, PARCHMENT_TEXT);
        if (menu.currentScience() > 0) {
            drawWrapped(graphics, materialLine(), line, PARCHMENT_TEXT);
        }
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int top, int color) {
        int line = top;
        for (FormattedCharSequence part : font.split(text, READOUT_WIDTH - 12)) {
            graphics.drawString(font, part, READOUT_X + 6, line, color, false);
            line += LINE_STEP;
        }
        return line + 2;
    }

    private Component factionLine() {
        return Component.translatable("screen.kingdoms.research_bench.faction_status", statusLine());
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
            case NO_MATERIALS -> PARCHMENT_TEXT;
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
}
