package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.InfluenceType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class InfluenceGainToast implements Toast {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/toast/influence.png");
    private static final ResourceLocation SCIENCE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/influence/science.png");
    private static final ResourceLocation ECONOMIC =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/influence/economic.png");
    private static final ResourceLocation MILITARY =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/influence/military.png");
    private static final long DISPLAY_TIME = 2800L;
    private static final int TOAST_WIDTH = 186;
    private static final int TOAST_HEIGHT = 34;
    private static final int TEXTURE_WIDTH = 236;
    private static final int TEXTURE_HEIGHT = 44;
    private static final int TEXT_LEFT = 36;

    private static String lastToastKey = "";
    private static long lastToastShownAt;

    private final InfluenceType type;
    private final long amount;

    public InfluenceGainToast(InfluenceType type, long amount) {
        this.type = type;
        this.amount = Math.max(0L, amount);
    }

    public static void show(InfluenceType type, long amount) {
        if (type == null || amount <= 0L) {
            return;
        }
        String toastKey = type.id() + ":" + amount;
        long now = System.currentTimeMillis();
        if (toastKey.equals(lastToastKey) && now - lastToastShownAt < DISPLAY_TIME) {
            return;
        }
        lastToastKey = toastKey;
        lastToastShownAt = now;
        Minecraft.getInstance().getToasts().addToast(new InfluenceGainToast(type, amount));
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceLastVisible) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.blit(
                BACKGROUND,
                0,
                0,
                width(),
                height(),
                0.0F,
                0.0F,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
        // The shared parchment texture has a dark icon slot; cover it before drawing our icon.
        graphics.blit(BACKGROUND, 6, 5, 38, height() - 10, 52, 8, 48, 30, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.fill(7, 7, 10, height() - 7, color());
        graphics.blit(icon(), 15, 9, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.drawString(
                minecraft.font,
                Component.translatable("screen.kingdoms.influence_gain", amount, Component.translatable(type.translationKey())),
                TEXT_LEFT,
                13,
                0xFF3A2A18,
                false
        );
        return timeSinceLastVisible >= DISPLAY_TIME ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public int width() {
        return TOAST_WIDTH;
    }

    @Override
    public int height() {
        return TOAST_HEIGHT;
    }

    private ResourceLocation icon() {
        return switch (type) {
            case SCIENCE -> SCIENCE;
            case ECONOMIC -> ECONOMIC;
            case MILITARY -> MILITARY;
        };
    }

    private int color() {
        return switch (type) {
            case SCIENCE -> 0xFF9AD0FF;
            case ECONOMIC -> 0xFFFFCE4A;
            case MILITARY -> 0xFFFF5A5A;
        };
    }
}
