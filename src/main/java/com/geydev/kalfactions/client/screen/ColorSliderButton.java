package com.geydev.kalfactions.client.screen;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public final class ColorSliderButton extends AbstractSliderButton {
    private final IntConsumer onColor;

    public ColorSliderButton(int x, int y, int width, int height, int initialColor, IntConsumer onColor) {
        super(x, y, width, height, Component.empty(), hueOf(initialColor));
        this.onColor = onColor;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("screen.kingdoms.color"));
    }

    @Override
    protected void applyValue() {
        onColor.accept(hueToColor((float) value));
    }

    private static int hueToColor(float hue) {
        float scaled = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) scaled % 6;
        float rising = scaled - (float) Math.floor(scaled);
        float falling = 1.0F - rising;
        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> { red = 1.0F; green = rising; blue = 0.0F; }
            case 1 -> { red = falling; green = 1.0F; blue = 0.0F; }
            case 2 -> { red = 0.0F; green = 1.0F; blue = rising; }
            case 3 -> { red = 0.0F; green = falling; blue = 1.0F; }
            case 4 -> { red = rising; green = 0.0F; blue = 1.0F; }
            default -> { red = 1.0F; green = 0.0F; blue = falling; }
        }
        return Math.round(red * 255.0F) << 16
                | Math.round(green * 255.0F) << 8
                | Math.round(blue * 255.0F);
    }

    private static double hueOf(int rgb) {
        float red = ((rgb >> 16) & 0xFF) / 255.0F;
        float green = ((rgb >> 8) & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        if (delta <= 0.0F) {
            return 0.0D;
        }
        float hue;
        if (max == red) {
            hue = ((green - blue) / delta) % 6.0F;
        } else if (max == green) {
            hue = (blue - red) / delta + 2.0F;
        } else {
            hue = (red - green) / delta + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        return hue;
    }
}
