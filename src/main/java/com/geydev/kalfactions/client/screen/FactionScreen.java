package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class FactionScreen extends Screen {
    protected static final int PANEL_WIDTH = 330;
    protected static final int PANEL_HEIGHT = 220;
    protected static final int CONTENT_LEFT = 28;
    protected static final int CONTENT_TOP = 46;
    protected static final int CONTENT_RIGHT = 298;
    protected static final int CONTENT_BOTTOM = 177;
    protected static final int TEXT_DARK = 0xFF3F2A19;
    protected static final int TEXT_MUTED = 0xFF4C3824;
    protected static final int TEXT_HINT = 0xFF5B452E;
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final long STATUS_FADE_IN_MILLIS = 150L;
    private static final long STATUS_HOLD_MILLIS = 3500L;
    private static final long STATUS_FADE_OUT_MILLIS = 450L;
    private static final int AUTO_REFRESH_TICKS = 40;

    private int autoRefreshTicks = AUTO_REFRESH_TICKS;

    protected FactionSnapshot snapshot;
    protected boolean successful;
    protected String statusMessage;
    protected int left;
    protected int top;
    private long statusShownAt;

    protected FactionScreen(Component title, FactionSnapshot snapshot, boolean successful, String statusMessage) {
        super(title);
        this.snapshot = snapshot;
        this.successful = successful;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
        this.statusShownAt = this.statusMessage.isBlank() ? 0L : System.currentTimeMillis();
    }

    public final BlockPos tablePos() {
        return snapshot.tablePos();
    }

    public final void acceptStatus(String message, boolean actionSuccessful) {
        if (message == null || message.isBlank()) {
            return;
        }
        statusMessage = message;
        successful = actionSuccessful;
        statusShownAt = System.currentTimeMillis();
    }

    public void acceptServerState(FactionSnapshot newSnapshot, boolean actionSuccessful, String message) {
        boolean factionPresenceChanged = snapshot.hasFaction() != newSnapshot.hasFaction();
        boolean snapshotChanged = !snapshot.equals(newSnapshot);
        snapshot = newSnapshot;
        acceptStatus(message, actionSuccessful);
        if (factionPresenceChanged) {
            FactionScreens.openRoot(newSnapshot, actionSuccessful, statusMessage);
        } else if (snapshotChanged) {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74, top + PANEL_HEIGHT - 25, 66, 20
        ));
        initFactionWidgets();
    }

    protected abstract void initFactionWidgets();

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, left + (PANEL_WIDTH - titleWidth) / 2, top + CONTENT_TOP + 2, TEXT_DARK, false);
        renderStatusNotice(graphics);
    }

    protected final void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    protected final void renderStatusNotice(GuiGraphics graphics) {
        if (statusMessage.isBlank() || statusShownAt == 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - statusShownAt;
        long total = STATUS_FADE_IN_MILLIS + STATUS_HOLD_MILLIS + STATUS_FADE_OUT_MILLIS;
        if (elapsed >= total) {
            return;
        }
        float alpha;
        if (elapsed < STATUS_FADE_IN_MILLIS) {
            alpha = elapsed / (float) STATUS_FADE_IN_MILLIS;
        } else if (elapsed < STATUS_FADE_IN_MILLIS + STATUS_HOLD_MILLIS) {
            alpha = 1.0F;
        } else {
            alpha = 1.0F - (elapsed - STATUS_FADE_IN_MILLIS - STATUS_HOLD_MILLIS) / (float) STATUS_FADE_OUT_MILLIS;
        }
        int alphaByte = (int) (alpha * 255.0F);
        if (alphaByte < 10) {
            return;
        }

        String clipped = font.plainSubstrByWidth(statusMessage, PANEL_WIDTH - 24);
        int textWidth = font.width(clipped);
        int boxWidth = textWidth + 16;
        int boxLeft = left + (PANEL_WIDTH - boxWidth) / 2;
        int boxTop = top + PANEL_HEIGHT + 5;
        if (boxTop + 19 > height) {
            boxTop = top + PANEL_HEIGHT - 19;
        }
        int background = (int) (alpha * 0xC0) << 24 | 0x101018;
        int accent = alphaByte << 24 | (successful ? 0x7FBF6F : 0xC05050);
        int textColor = alphaByte << 24 | (successful ? 0xC9F0B8 : 0xF3B8B8);
        graphics.fill(boxLeft, boxTop, boxLeft + boxWidth, boxTop + 17, background);
        graphics.fill(boxLeft, boxTop, boxLeft + boxWidth, boxTop + 1, accent);
        graphics.drawString(font, clipped, boxLeft + 8, boxTop + 5, textColor, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (--autoRefreshTicks <= 0) {
            autoRefreshTicks = AUTO_REFRESH_TICKS;
            PacketDistributor.sendToServer(new FactionPayloads.C2SOpenTable(snapshot.tablePos(), true));
        }
    }

    protected final void requestRefresh() {
        PacketDistributor.sendToServer(new FactionPayloads.C2SOpenTable(snapshot.tablePos(), false));
    }

    protected static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
