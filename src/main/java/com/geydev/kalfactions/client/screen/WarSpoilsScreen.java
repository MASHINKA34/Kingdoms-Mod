package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WarSpoilsScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF4C3824;
    private static final int TEXT_HINT = 0xFF5B452E;
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");

    private final FactionSnapshot.WarSpoils spoils;
    private int left;
    private int top;

    public WarSpoilsScreen(FactionSnapshot.WarSpoils spoils) {
        super(Component.translatable("screen.kingdoms.war_spoils_title"));
        this.spoils = spoils == null ? FactionSnapshot.WarSpoils.EMPTY : spoils;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        addChoice("MONEY", "screen.kingdoms.war_spoils_money", top + 94);
        addChoice("RESOURCES", "screen.kingdoms.war_spoils_resources_choice", top + 118);
        addChoice("SPLIT", "screen.kingdoms.war_spoils_split", top + 142);
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.cancel"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 25,
                66,
                20
        ));
    }

    private void addChoice(String choice, String labelKey, int y) {
        KingdomsButton button = addRenderableWidget(KingdomsButton.create(
                Component.translatable(labelKey),
                ignored -> {
                    PacketDistributor.sendToServer(new FactionPayloads.C2SClaimWarSpoils(spoils.spoilsId(), choice));
                    onClose();
                },
                left + CONTENT_LEFT,
                y,
                126,
                20
        ));
        button.active = spoils.hasSpoils();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int titleWidth = font.width(title);
        graphics.drawString(font, title, left + (PANEL_WIDTH - titleWidth) / 2, top + 48, TEXT_DARK, false);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.war_spoils_loser", spoils.loserName()),
                left + CONTENT_LEFT,
                top + 64,
                TEXT_MUTED,
                false
        );

        List<FormattedCharSequence> hintLines = font.split(
                Component.translatable("screen.kingdoms.war_spoils_hint"),
                270
        );
        for (int index = 0; index < Math.min(2, hintLines.size()); index++) {
            graphics.drawString(font, hintLines.get(index), left + CONTENT_LEFT, top + 76 + index * 9, TEXT_HINT, false);
        }

        drawAmount(graphics, top + 100, Component.translatable("screen.kingdoms.war_spoils_money_amount", spoils.money()));
        drawAmount(graphics, top + 124, resourcesAmount(spoils.science(), spoils.economic(), spoils.military()));
        drawAmount(graphics, top + 148, Component.translatable(
                "screen.kingdoms.war_spoils_split_amount",
                spoils.money() / 2L,
                spoils.science() / 2L,
                spoils.economic() / 2L,
                spoils.military() / 2L
        ));
    }

    private void drawAmount(GuiGraphics graphics, int y, Component text) {
        String clipped = font.plainSubstrByWidth(text.getString(), 136);
        graphics.drawString(font, clipped, left + CONTENT_LEFT + 136, y, TEXT_DARK, false);
    }

    private static Component resourcesAmount(long science, long economic, long military) {
        return Component.translatable("screen.kingdoms.war_spoils_resources_amount", science, economic, military);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
