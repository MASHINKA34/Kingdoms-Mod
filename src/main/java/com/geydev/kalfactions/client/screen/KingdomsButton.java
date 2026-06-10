package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class KingdomsButton extends Button {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/button.png");
    private static final int TEXTURE_WIDTH = 200;
    private static final int TEXTURE_HEIGHT = 60;
    private static final int STATE_HEIGHT = 20;
    private static final int BORDER = 4;

    private KingdomsButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public static KingdomsButton create(Component message, OnPress onPress, int x, int y, int width, int height) {
        return new KingdomsButton(x, y, width, height, message, onPress);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int stateV = !active ? STATE_HEIGHT * 2 : isHoveredOrFocused() ? STATE_HEIGHT : 0;
        drawNineSlice(graphics, getX(), getY(), getWidth(), getHeight(), stateV);
        int textColor = active ? 0xFFF6E6C5 : 0xFF8E8B83;
        int alpha = Mth.ceil(this.alpha * 255.0F) << 24;
        renderString(graphics, Minecraft.getInstance().font, (textColor & 0xFFFFFF) | alpha);
    }

    private static void drawNineSlice(GuiGraphics graphics, int x, int y, int width, int height, int v) {
        int innerWidth = Math.max(0, width - BORDER * 2);
        int innerHeight = Math.max(0, height - BORDER * 2);
        int textureInnerWidth = TEXTURE_WIDTH - BORDER * 2;
        int textureInnerHeight = STATE_HEIGHT - BORDER * 2;

        graphics.blit(TEXTURE, x, y, BORDER, BORDER,
                0, v, BORDER, BORDER, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(TEXTURE, x + width - BORDER, y, BORDER, BORDER,
                TEXTURE_WIDTH - BORDER, v, BORDER, BORDER, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(TEXTURE, x, y + height - BORDER, BORDER, BORDER,
                0, v + STATE_HEIGHT - BORDER, BORDER, BORDER, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.blit(TEXTURE, x + width - BORDER, y + height - BORDER, BORDER, BORDER,
                TEXTURE_WIDTH - BORDER, v + STATE_HEIGHT - BORDER, BORDER, BORDER, TEXTURE_WIDTH, TEXTURE_HEIGHT);

        if (innerWidth > 0) {
            graphics.blit(TEXTURE, x + BORDER, y, innerWidth, BORDER,
                    BORDER, v, textureInnerWidth, BORDER, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(TEXTURE, x + BORDER, y + height - BORDER, innerWidth, BORDER,
                    BORDER, v + STATE_HEIGHT - BORDER, textureInnerWidth, BORDER, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        if (innerHeight > 0) {
            graphics.blit(TEXTURE, x, y + BORDER, BORDER, innerHeight,
                    0, v + BORDER, BORDER, textureInnerHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(TEXTURE, x + width - BORDER, y + BORDER, BORDER, innerHeight,
                    TEXTURE_WIDTH - BORDER, v + BORDER, BORDER, textureInnerHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        if (innerWidth > 0 && innerHeight > 0) {
            graphics.blit(TEXTURE, x + BORDER, y + BORDER, innerWidth, innerHeight,
                    BORDER, v + BORDER, textureInnerWidth, textureInnerHeight, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }
}
