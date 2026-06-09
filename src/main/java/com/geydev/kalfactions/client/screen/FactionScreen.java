package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class FactionScreen extends Screen {
    protected static final int PANEL_WIDTH = 330;
    protected static final int PANEL_HEIGHT = 220;
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    protected static final int[] COLORS = {
            0xB3312C, 0xD88198, 0x3B511A, 0x41CD34,
            0x253193, 0x7B2FBE, 0x287697, 0xABABAB
    };
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
        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose()
        ).bounds(left + PANEL_WIDTH - 58, top + PANEL_HEIGHT - 25, 50, 20).build());
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
        graphics.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 12, 0xFFF3D58B);
        if (!statusMessage.isBlank()) {
            int color = successful ? 0x2E5D2E : 0x8B2525;
            graphics.drawString(font, statusMessage, left + 12, top + PANEL_HEIGHT - 22, color, false);
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

    protected static int nextColor(int current) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == (current & 0xFFFFFF)) {
                return COLORS[(i + 1) % COLORS.length];
            }
        }
        return COLORS[0];
    }
}
