package com.geydev.kalfactions.client.screen;

import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class ColorPickerScreen extends Screen {
    private static final int PANEL_WIDTH = 230;
    private static final int PANEL_HEIGHT = 150;
    private static final int SV_SIZE = 96;
    private static final int HUE_WIDTH = 14;

    private final Screen parent;
    private final IntConsumer onAccept;

    private float hue;
    private float saturation;
    private float value;

    private int panelLeft;
    private int panelTop;
    private int svLeft;
    private int svTop;
    private int hueLeft;

    private EditBox hexBox;
    private boolean draggingSv;
    private boolean draggingHue;
    private boolean updatingHexBox;

    public ColorPickerScreen(Screen parent, int initialColor, IntConsumer onAccept) {
        super(Component.translatable("screen.kingdoms.color_picker"));
        this.parent = parent;
        this.onAccept = onAccept;
        float[] hsv = rgbToHsv(initialColor);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        svLeft = panelLeft + 12;
        svTop = panelTop + 28;
        hueLeft = svLeft + SV_SIZE + 8;

        int controlsLeft = hueLeft + HUE_WIDTH + 10;
        int controlsWidth = panelLeft + PANEL_WIDTH - 12 - controlsLeft;

        hexBox = new EditBox(font, controlsLeft, svTop + 32, controlsWidth, 16, Component.literal("HEX"));
        hexBox.setMaxLength(7);
        hexBox.setFilter(text -> text.matches("#?[0-9a-fA-F]{0,6}"));
        hexBox.setResponder(this::onHexTyped);
        addRenderableWidget(hexBox);
        pushHexBox();

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.apply"),
                button -> {
                    onAccept.accept(currentColor());
                    minecraft.setScreen(parent);
                },
                controlsLeft, svTop + 56, controlsWidth, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.cancel"),
                button -> onClose(),
                controlsLeft, svTop + 80, controlsWidth, 20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int titleWidth = font.width(title);
        graphics.drawString(font, title, panelLeft + (PANEL_WIDTH - titleWidth) / 2, panelTop + 10, 0xFFF3D58B, true);

        renderSvSquare(graphics);
        renderHueBar(graphics);

        int controlsLeft = hueLeft + HUE_WIDTH + 10;
        int controlsWidth = panelLeft + PANEL_WIDTH - 12 - controlsLeft;
        int color = currentColor();
        graphics.fill(controlsLeft - 1, svTop - 1, controlsLeft + controlsWidth + 1, svTop + 21, 0xFF1A140C);
        graphics.fill(controlsLeft, svTop, controlsLeft + controlsWidth, svTop + 20, 0xFF000000 | color);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, 0xFFC9A24C);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF2B2E38);
    }

    private void renderSvSquare(GuiGraphics graphics) {
        graphics.fill(svLeft - 1, svTop - 1, svLeft + SV_SIZE + 1, svTop + SV_SIZE + 1, 0xFF1A140C);
        int hueColor = Mth.hsvToRgb(hue, 1.0F, 1.0F);
        for (int column = 0; column < SV_SIZE; column++) {
            float sat = column / (float) (SV_SIZE - 1);
            int top = 0xFF000000 | lerpColor(0xFFFFFF, hueColor, sat);
            graphics.fillGradient(svLeft + column, svTop, svLeft + column + 1, svTop + SV_SIZE, top, 0xFF000000);
        }
        int cursorX = svLeft + Math.round(saturation * (SV_SIZE - 1));
        int cursorY = svTop + Math.round((1.0F - value) * (SV_SIZE - 1));
        graphics.fill(cursorX - 2, cursorY - 2, cursorX + 3, cursorY + 3, 0xFFFFFFFF);
        graphics.fill(cursorX - 1, cursorY - 1, cursorX + 2, cursorY + 2, 0xFF000000 | currentColor());
    }

    private void renderHueBar(GuiGraphics graphics) {
        graphics.fill(hueLeft - 1, svTop - 1, hueLeft + HUE_WIDTH + 1, svTop + SV_SIZE + 1, 0xFF1A140C);
        int segments = 6;
        int segmentHeight = SV_SIZE / segments;
        for (int segment = 0; segment < segments; segment++) {
            int from = 0xFF000000 | Mth.hsvToRgb(segment / (float) segments, 1.0F, 1.0F);
            int to = 0xFF000000 | Mth.hsvToRgb((segment + 1) / (float) segments, 1.0F, 1.0F);
            int y = svTop + segment * segmentHeight;
            int bottom = segment == segments - 1 ? svTop + SV_SIZE : y + segmentHeight;
            graphics.fillGradient(hueLeft, y, hueLeft + HUE_WIDTH, bottom, from, to);
        }
        int cursorY = svTop + Math.round(hue * (SV_SIZE - 1));
        graphics.fill(hueLeft - 1, cursorY - 1, hueLeft + HUE_WIDTH + 1, cursorY + 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (insideSv(mouseX, mouseY)) {
                draggingSv = true;
                updateSv(mouseX, mouseY);
                return true;
            }
            if (insideHue(mouseX, mouseY)) {
                draggingHue = true;
                updateHue(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSv) {
            updateSv(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSv = false;
        draggingHue = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean insideSv(double mouseX, double mouseY) {
        return mouseX >= svLeft && mouseX < svLeft + SV_SIZE && mouseY >= svTop && mouseY < svTop + SV_SIZE;
    }

    private boolean insideHue(double mouseX, double mouseY) {
        return mouseX >= hueLeft && mouseX < hueLeft + HUE_WIDTH && mouseY >= svTop && mouseY < svTop + SV_SIZE;
    }

    private void updateSv(double mouseX, double mouseY) {
        saturation = Mth.clamp((float) (mouseX - svLeft) / (SV_SIZE - 1), 0.0F, 1.0F);
        value = 1.0F - Mth.clamp((float) (mouseY - svTop) / (SV_SIZE - 1), 0.0F, 1.0F);
        pushHexBox();
    }

    private void updateHue(double mouseY) {
        hue = Mth.clamp((float) (mouseY - svTop) / (SV_SIZE - 1), 0.0F, 1.0F);
        pushHexBox();
    }

    private void onHexTyped(String text) {
        if (updatingHexBox) {
            return;
        }
        String digits = text.startsWith("#") ? text.substring(1) : text;
        if (digits.length() != 6) {
            return;
        }
        int color;
        try {
            color = Integer.parseInt(digits, 16);
        } catch (NumberFormatException exception) {
            return;
        }
        float[] hsv = rgbToHsv(color);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    private void pushHexBox() {
        if (hexBox == null) {
            return;
        }
        updatingHexBox = true;
        hexBox.setValue(String.format(Locale.ROOT, "#%06X", currentColor()));
        updatingHexBox = false;
    }

    private int currentColor() {
        return Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
    }

    private static int lerpColor(int from, int to, float t) {
        int red = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return red << 16 | green << 8 | blue;
    }

    private static float[] rgbToHsv(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        float hue;
        if (delta <= 0.0F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = (((green - blue) / delta) % 6.0F) / 6.0F;
        } else if (max == green) {
            hue = ((blue - red) / delta + 2.0F) / 6.0F;
        } else {
            hue = ((red - green) / delta + 4.0F) / 6.0F;
        }
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max <= 0.0F ? 0.0F : delta / max;
        return new float[] {hue, saturation, max};
    }
}
