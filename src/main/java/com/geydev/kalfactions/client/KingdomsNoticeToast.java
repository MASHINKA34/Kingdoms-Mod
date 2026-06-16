package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class KingdomsNoticeToast implements Toast {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/toast/influence.png");
    private static final long DISPLAY_TIME = 4200L;
    private static final int TOAST_WIDTH = 200;
    private static final int TOAST_HEIGHT = 48;

    private final Component message;
    private final boolean successful;

    public KingdomsNoticeToast(Component message, boolean successful) {
        this.message = message;
        this.successful = successful;
    }

    public static void show(Component message, boolean successful) {
        if (message == null || message.getString().isBlank()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(new KingdomsNoticeToast(message, successful));
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent component, long timeSinceLastVisible) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.blit(BACKGROUND, 0, 0, width(), height(), 0.0F, 0.0F, 256, 64, 256, 64);
        graphics.fill(8, 8, 11, height() - 8, successful ? 0xFF5AFF8A : 0xFFFF5A5A);
        List<FormattedCharSequence> lines = minecraft.font.split(message, width() - 28);
        int color = successful ? 0xFFE8D6A0 : 0xFFFFD9D9;
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            graphics.drawString(minecraft.font, lines.get(i), 18, 10 + i * 10, color, false);
        }
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
}
