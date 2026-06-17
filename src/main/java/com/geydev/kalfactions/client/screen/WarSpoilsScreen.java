package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import dev.ithundxr.createnumismatics.content.backend.Coin;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WarSpoilsScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int BUTTON_WIDTH = 116;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF4C3824;
    private static final int TEXT_HINT = 0xFF5B452E;
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");

    private final FactionSnapshot.WarSpoils spoils;
    private ItemStack moneyIcon = ItemStack.EMPTY;
    private ItemStack resourceOneIcon = ItemStack.EMPTY;
    private ItemStack resourceTwoIcon = ItemStack.EMPTY;
    private ItemStack resourceThreeIcon = ItemStack.EMPTY;
    private final List<IconHover> hovers = new ArrayList<>();
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
        moneyIcon = Coin.SPUR.asStack();
        resourceOneIcon = iconFor(spoils.resourceOneItem());
        resourceTwoIcon = iconFor(spoils.resourceTwoItem());
        resourceThreeIcon = iconFor(spoils.resourceThreeItem());
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
                BUTTON_WIDTH,
                20
        ));
        button.active = spoils.hasSpoils();
    }

    private static ItemStack iconFor(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        hovers.clear();

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

        int amountX = left + CONTENT_LEFT + BUTTON_WIDTH + 6;
        drawAmount(graphics, amountX, top + 94, spoils.money(), moneyIcon);
        drawResources(graphics, amountX, top + 118,
                spoils.resourceOne(), spoils.resourceTwo(), spoils.resourceThree());
        int splitX = drawAmount(graphics, amountX, top + 142, spoils.money() / 2L, moneyIcon);
        drawResources(graphics, splitX, top + 142,
                spoils.resourceOne() / 2L, spoils.resourceTwo() / 2L, spoils.resourceThree() / 2L);

        for (IconHover hover : hovers) {
            if (mouseX >= hover.x() && mouseX < hover.x() + 16 && mouseY >= hover.y() && mouseY < hover.y() + 16) {
                graphics.renderTooltip(font, hover.icon().getHoverName(), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawResources(GuiGraphics graphics, int x, int rowY, long one, long two, long three) {
        x = drawAmount(graphics, x, rowY, one, resourceOneIcon);
        x = drawAmount(graphics, x, rowY, two, resourceTwoIcon);
        drawAmount(graphics, x, rowY, three, resourceThreeIcon);
    }

    private int drawAmount(GuiGraphics graphics, int x, int rowY, long count, ItemStack icon) {
        if (count <= 0L && icon.isEmpty()) {
            return x;
        }
        String text = Long.toString(count);
        graphics.drawString(font, text, x, rowY + 6, TEXT_DARK, false);
        int iconX = x + font.width(text) + 2;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, iconX, rowY + 2);
            hovers.add(new IconHover(iconX, rowY + 2, icon));
            return iconX + 16 + 6;
        }
        return iconX + 6;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record IconHover(int x, int y, ItemStack icon) {
    }
}
