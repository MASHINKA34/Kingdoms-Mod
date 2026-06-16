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
    private static final long DISPLAY_TIME = 4500L;
    private static final int TOAST_WIDTH = 236;
    private static final int TOAST_HEIGHT = 44;

    private final InfluenceType type;
    private final long amount;

    public InfluenceGainToast(InfluenceType type, long amount) {
        this.type = type;
        this.amount = Math.max(0L, amount);
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceLastVisible) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.blit(BACKGROUND, 0, 0, width(), height(), 0.0F, 0.0F, width(), height(), width(), height());
        graphics.blit(icon(), 14, 12, 20, 20, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.drawString(
                minecraft.font,
                Component.translatable("screen.kingdoms.influence_gain", amount, Component.translatable(type.translationKey())),
                42,
                17,
                color(),
                true
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
