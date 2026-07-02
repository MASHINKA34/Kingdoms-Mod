package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.market.MarketPayloads;
import com.geydev.kalfactions.market.MarketPlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PlotScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int BUTTON_WIDTH = 130;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF4C3824;
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");

    private final MarketPayloads.S2COpenPlotScreen data;
    private final MarketPlot.State state;
    private EditBox priceBox;
    private KingdomsButton sellToServerButton;
    private boolean confirmSell;
    private int left;
    private int top;

    private PlotScreen(MarketPayloads.S2COpenPlotScreen data) {
        super(Component.translatable("screen.kingdoms.plot_title", data.plotId()));
        this.data = data;
        MarketPlot.State[] states = MarketPlot.State.values();
        this.state = states[Math.floorMod(data.state(), states.length)];
    }

    public static void handle(MarketPayloads.S2COpenPlotScreen payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(new PlotScreen(payload)));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        if (data.isOwner()) {
            initOwner();
        } else {
            initBuyer();
        }
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.plot_close"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 25,
                66,
                20
        ));
    }

    private void initBuyer() {
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.plot_buy"),
                button -> {
                    PacketDistributor.sendToServer(new MarketPayloads.C2SBuyPlot(data.plotId()));
                    onClose();
                },
                left + CONTENT_LEFT,
                top + 150,
                BUTTON_WIDTH,
                20
        ));
    }

    private void initOwner() {
        if (state == MarketPlot.State.RESALE) {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.plot_cancel_resale"),
                    button -> {
                        PacketDistributor.sendToServer(new MarketPayloads.C2SManagePlot(
                                data.plotId(), MarketPayloads.C2SManagePlot.ACTION_CANCEL_RESALE, 0L));
                        onClose();
                    },
                    left + CONTENT_LEFT,
                    top + 96,
                    BUTTON_WIDTH,
                    20
            ));
        } else {
            priceBox = new EditBox(
                    font,
                    left + CONTENT_LEFT,
                    top + 96,
                    100,
                    18,
                    Component.translatable("screen.kingdoms.plot_price_hint")
            );
            priceBox.setFilter(text -> text.matches("\\d{0,9}"));
            priceBox.setHint(Component.translatable("screen.kingdoms.plot_price_hint"));
            addRenderableWidget(priceBox);
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.plot_list_resale"),
                    button -> {
                        long price = parsePrice();
                        if (price > 0L) {
                            PacketDistributor.sendToServer(new MarketPayloads.C2SManagePlot(
                                    data.plotId(), MarketPayloads.C2SManagePlot.ACTION_LIST_RESALE, price));
                            onClose();
                        }
                    },
                    left + CONTENT_LEFT + 106,
                    top + 95,
                    BUTTON_WIDTH,
                    20
            ));
        }
        sellToServerButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.plot_sell_server",
                        NumismaticsEconomy.format(data.buybackAmount())),
                button -> {
                    if (!confirmSell) {
                        confirmSell = true;
                        sellToServerButton.setMessage(
                                Component.translatable("screen.kingdoms.plot_sell_server_confirm"));
                        return;
                    }
                    PacketDistributor.sendToServer(new MarketPayloads.C2SManagePlot(
                            data.plotId(), MarketPayloads.C2SManagePlot.ACTION_SELL_TO_SERVER, 0L));
                    onClose();
                },
                left + CONTENT_LEFT,
                top + 150,
                BUTTON_WIDTH + 60,
                20
        ));
    }

    private long parsePrice() {
        if (priceBox == null || priceBox.getValue().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(priceBox.getValue());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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

        if (data.isOwner()) {
            graphics.drawString(font, Component.translatable("screen.kingdoms.plot_yours"),
                    left + CONTENT_LEFT, top + 66, TEXT_MUTED, false);
            if (state == MarketPlot.State.RESALE) {
                graphics.drawString(font, Component.translatable("screen.kingdoms.plot_listed_for",
                                NumismaticsEconomy.format(data.askingPrice())),
                        left + CONTENT_LEFT, top + 80, TEXT_MUTED, false);
            } else {
                graphics.drawString(font, Component.translatable("screen.kingdoms.plot_resale_prompt"),
                        left + CONTENT_LEFT, top + 80, TEXT_MUTED, false);
            }
        } else {
            graphics.drawString(font, Component.translatable("screen.kingdoms.plot_price",
                            NumismaticsEconomy.format(data.askingPrice())),
                    left + CONTENT_LEFT, top + 66, TEXT_MUTED, false);
            if (state == MarketPlot.State.RESALE && !data.ownerName().isEmpty()) {
                graphics.drawString(font, Component.translatable("screen.kingdoms.plot_seller",
                                data.ownerName()),
                        left + CONTENT_LEFT, top + 80, TEXT_MUTED, false);
            }
            graphics.drawString(font, Component.translatable("screen.kingdoms.plot_buy_hint"),
                    left + CONTENT_LEFT, top + 96, TEXT_MUTED, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
