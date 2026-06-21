package com.geydev.kalfactions.client;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
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
    private static final long DEFAULT_DISPLAY_TIME = 6000L;
    private static final int TOAST_WIDTH = 186;
    private static final int TOAST_HEIGHT = 34;
    private static final int TEXTURE_WIDTH = 236;
    private static final int TEXTURE_HEIGHT = 44;

    private static String lastNoticeKey = "";
    private static long lastNoticeShownAt;

    private final Component message;
    private final boolean successful;
    private final long displayTime;

    public KingdomsNoticeToast(Component message, boolean successful) {
        this.message = message;
        this.successful = successful;
        this.displayTime = displayTimeMillis();
    }

    private static long displayTimeMillis() {
        try {
            return ModConfigSpec.RAID_TOAST_SECONDS.getAsInt() * 1000L;
        } catch (IllegalStateException configNotLoaded) {
            return DEFAULT_DISPLAY_TIME;
        }
    }

    public static void show(Component message, boolean successful) {
        if (message == null || message.getString().isBlank()) {
            return;
        }
        String noticeKey = successful + ":" + message.getString();
        long now = System.currentTimeMillis();
        if (noticeKey.equals(lastNoticeKey) && now - lastNoticeShownAt < displayTimeMillis()) {
            return;
        }
        lastNoticeKey = noticeKey;
        lastNoticeShownAt = now;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(new KingdomsNoticeToast(message, successful));
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
        graphics.blit(BACKGROUND, 6, 5, 38, height() - 10, 52, 8, 48, 30, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        graphics.fill(7, 7, 10, height() - 7, successful ? 0xFF3FB85B : 0xFFC8463C);
        int textLeft = 16;
        List<FormattedCharSequence> lines = minecraft.font.split(message, width() - textLeft - 8);
        int lineCount = Math.min(2, lines.size());
        int startY = (height() - lineCount * 10) / 2 + 1;
        for (int i = 0; i < lineCount; i++) {
            graphics.drawString(minecraft.font, lines.get(i), textLeft, startY + i * 10, 0xFF3A2A18, false);
        }
        return timeSinceLastVisible >= displayTime ? Visibility.HIDE : Visibility.SHOW;
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
