package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionInfluenceScreen extends FactionScreen {
    private static final ResourceLocation ICON_SCIENCE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/influence/science.png");
    private static final ResourceLocation ICON_ECONOMIC =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/influence/economic.png");
    private static final ResourceLocation ICON_MILITARY =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/influence/military.png");

    public FactionInfluenceScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.influence"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.turn_in_crystals"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2STurnInCrystals(snapshot.tablePos())),
                left + CONTENT_LEFT, top + 142, 180, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.research_open"),
                button -> FactionScreens.openResearch(snapshot, true, ""),
                left + CONTENT_LEFT + 188, top + 142, 82, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openRoot(snapshot, true, ""),
                left + 16, top + PANEL_HEIGHT - 25, 70, 20
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRow(graphics, ICON_SCIENCE, "kingdoms.influence.science", snapshot.influenceScience(), top + 64);
        renderRow(graphics, ICON_ECONOMIC, "kingdoms.influence.economic", snapshot.influenceEconomic(), top + 88);
        renderRow(graphics, ICON_MILITARY, "kingdoms.influence.military", snapshot.influenceMilitary(), top + 112);
        graphics.drawString(
                font,
                text("screen.kingdoms.turn_in_hint"),
                left + CONTENT_LEFT,
                top + 132,
                TEXT_HINT,
                false
        );
    }

    private void renderRow(GuiGraphics graphics, ResourceLocation icon, String key, long value, int y) {
        graphics.blit(icon, left + CONTENT_LEFT, y, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.drawString(
                font,
                text("screen.kingdoms.influence_row", text(key), value),
                left + CONTENT_LEFT + 22,
                y + 4,
                TEXT_DARK,
                false
        );
    }
}
