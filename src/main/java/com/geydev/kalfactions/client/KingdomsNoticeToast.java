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
    private static final int TOAST_WIDTH = 236;
    private static final int TOAST_HEIGHT = 44;

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
        graphics.blit(BACKGROUND, 0, 0, width(), height(), 0.0F, 0.0F, width(), height(), width(), height());
        graphics.fill(13, 11, 35, 33, 0xFF2A1C0E);
        graphics.fill(15, 13, 33, 31, successful ? 0xFF3FB85B : 0xFFC8463C);
        int textLeft = 42;
        List<FormattedCharSequence> lines = minecraft.font.split(message, width() - textLeft - 10);
        int lineCount = Math.min(3, lines.size());
        int startY = (height() - lineCount * 10) / 2 + 1;
        for (int i = 0; i < lineCount; i++) {
            graphics.drawString(minecraft.font, lines.get(i), textLeft, startY + i * 10, 0xFF3A2A18, false);
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
