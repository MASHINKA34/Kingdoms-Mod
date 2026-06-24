package com.geydev.kalfactions.client.screen;

import net.minecraft.client.gui.GuiGraphics;

public final class KingdomsPanel {
    public static final int BORDER = 0xFFC9A24C;
    public static final int BACKGROUND = 0xFF2B2E38;

    public static void draw(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left - 1, top - 1, left + width + 1, top + height + 1, BORDER);
        graphics.fill(left, top, left + width, top + height, BACKGROUND);
    }

    private KingdomsPanel() {
    }
}
