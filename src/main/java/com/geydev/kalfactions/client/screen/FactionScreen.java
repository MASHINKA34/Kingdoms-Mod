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
    protected FactionSnapshot snapshot;
    protected boolean successful;
    protected String statusMessage;
    protected int left;
    protected int top;

    protected FactionScreen(Component title, FactionSnapshot snapshot, boolean successful, String statusMessage) {
        super(title);
        this.snapshot = snapshot;
        this.successful = successful;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
    }

    public final BlockPos tablePos() {
        return snapshot.tablePos();
    }

    public void acceptServerState(FactionSnapshot newSnapshot, boolean actionSuccessful, String message) {
        boolean factionPresenceChanged = snapshot.hasFaction() != newSnapshot.hasFaction();
        snapshot = newSnapshot;
        successful = actionSuccessful;
        statusMessage = message == null ? "" : message;
        if (factionPresenceChanged) {
            FactionScreens.openRoot(newSnapshot, actionSuccessful, statusMessage);
        } else {
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
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, left + (PANEL_WIDTH - titleWidth) / 2, top + CONTENT_TOP + 2, TEXT_DARK, false);
        if (!statusMessage.isBlank()) {
            int color = successful ? 0xFF9FE09F : 0xFFE89090;
            String clipped = font.plainSubstrByWidth(statusMessage, PANEL_WIDTH - 56);
            graphics.drawString(font, clipped, left + CONTENT_LEFT, top + 181, color, true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected final void requestRefresh() {
        PacketDistributor.sendToServer(new FactionPayloads.C2SOpenTable(snapshot.tablePos()));
    }

    protected static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
