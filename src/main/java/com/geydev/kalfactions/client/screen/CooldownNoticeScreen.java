package com.geydev.kalfactions.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Styled plaque shown when the leader tries to declare a war while the faction is still on its
 * post-war cooldown. Displays a live countdown of the time remaining.
 */
public final class CooldownNoticeScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 150;

    private final Screen parent;
    private final long endTimeMillis;

    private int panelLeft;
    private int panelTop;

    public CooldownNoticeScreen(Screen parent, long remainingSeconds) {
        super(Component.translatable("screen.kingdoms.war_cooldown_title"));
        this.parent = parent;
        this.endTimeMillis = System.currentTimeMillis() + Math.max(0L, remainingSeconds) * 1000L;
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.ok"),
                button -> onClose(),
                panelLeft + (PANEL_WIDTH - 110) / 2, panelTop + PANEL_HEIGHT - 30, 110, 20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int centerX = panelLeft + PANEL_WIDTH / 2;
        int titleWidth = font.width(title);
        graphics.drawString(font, title, centerX - titleWidth / 2, panelTop + 14, 0xFFF3D58B, true);

        long remaining = Math.max(0L, (endTimeMillis - System.currentTimeMillis()) / 1000L);
        Component description = remaining > 0L
                ? Component.translatable("screen.kingdoms.war_cooldown_desc")
                : Component.translatable("screen.kingdoms.war_cooldown_ready");
        int descWidth = font.width(description);
        graphics.drawString(font, description, centerX - descWidth / 2, panelTop + 46, 0xFFC9C4BA, true);

        if (remaining > 0L) {
            String time = formatRemaining(remaining);
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, panelTop + 78, 0);
            graphics.pose().scale(1.8F, 1.8F, 1.0F);
            graphics.drawString(font, time, -font.width(time) / 2, 0, 0xFFE8B84B, true);
            graphics.pose().popPose();
        }
    }

    private String formatRemaining(long seconds) {
        long days = seconds / 86_400L;
        long hours = (seconds % 86_400L) / 3_600L;
        long minutes = (seconds % 3_600L) / 60L;
        long secs = seconds % 60L;
        if (days > 0L) {
            return unit(days, "d") + " " + unit(hours, "h");
        }
        if (hours > 0L) {
            return unit(hours, "h") + " " + unit(minutes, "m");
        }
        if (minutes > 0L) {
            return unit(minutes, "m") + " " + unit(secs, "s");
        }
        return unit(secs, "s");
    }

    private static String unit(long value, String key) {
        return value + Component.translatable("kingdoms.time." + key).getString();
    }

    /** Single most-significant unit, e.g. "47h" / "2d" / "5m" — for compact labels like buttons. */
    public static String compactRemaining(long seconds) {
        if (seconds >= 86_400L) {
            return unit(seconds / 86_400L, "d");
        }
        if (seconds >= 3_600L) {
            return unit(seconds / 3_600L, "h");
        }
        if (seconds >= 60L) {
            return unit(seconds / 60L, "m");
        }
        return unit(Math.max(0L, seconds), "s");
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        KingdomsPanel.draw(graphics, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
