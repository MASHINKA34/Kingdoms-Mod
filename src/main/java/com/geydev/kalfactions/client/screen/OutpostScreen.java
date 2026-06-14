package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.OutpostPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OutpostScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;

    private final BlockPos core;
    private final String factionName;
    private final boolean canManage;
    private int left;
    private int top;

    public OutpostScreen(OutpostPayloads.S2COutpostState state) {
        super(Component.translatable("screen.kingdoms.outpost.title"));
        this.core = state.core();
        this.factionName = state.factionName();
        this.canManage = state.canManage();
    }

    public static void open(OutpostPayloads.S2COutpostState state) {
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new OutpostScreen(state)));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;

        KingdomsButton dismantle = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.outpost.dismantle"),
                button -> {
                    PacketDistributor.sendToServer(new OutpostPayloads.C2SDismantleOutpost(core));
                    onClose();
                },
                left + (PANEL_WIDTH - 180) / 2, top + 110, 180, 20
        ));
        dismantle.active = canManage;

        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74, top + PANEL_HEIGHT - 25, 66, 20
        ));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 48, TEXT_DARK, false);
        Component faction = Component.translatable("screen.kingdoms.outpost.faction", factionName);
        graphics.drawString(font, faction, left + (PANEL_WIDTH - font.width(faction)) / 2, top + 72, TEXT_DARK, false);
        Component info = Component.translatable("screen.kingdoms.outpost.info");
        graphics.drawString(font, info, left + (PANEL_WIDTH - font.width(info)) / 2, top + 90, TEXT_MUTED, false);
        if (!canManage) {
            Component note = Component.translatable("screen.kingdoms.outpost.officer_only");
            graphics.drawString(font, note, left + (PANEL_WIDTH - font.width(note)) / 2, top + 140, TEXT_MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
