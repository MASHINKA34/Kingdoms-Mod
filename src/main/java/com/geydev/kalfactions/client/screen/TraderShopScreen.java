package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.trader.TraderOffer;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraderShopScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int TEXT_SUCCESS = 0xFF427A36;
    private static final int TEXT_FAILURE = 0xFFA33B32;

    private final UUID traderId;
    private List<TraderPayloads.OfferInfo> offers;
    private Component notice;
    private boolean successful;
    private String pendingOfferId = "";
    private int left;
    private int top;

    public TraderShopScreen(TraderPayloads.S2CShopState state) {
        super(Component.translatable("screen.kingdoms.trader.title"));
        traderId = state.traderId();
        offers = state.offers();
        notice = state.notice();
        successful = state.successful();
    }

    public static void handle(TraderPayloads.S2CShopState state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof TraderShopScreen screen
                    && screen.traderId.equals(state.traderId())) {
                screen.acceptState(state);
            } else {
                minecraft.setScreen(new TraderShopScreen(state));
            }
        });
    }

    private void acceptState(TraderPayloads.S2CShopState state) {
        offers = state.offers();
        notice = state.notice();
        successful = state.successful();
        pendingOfferId = "";
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        for (int i = 0; i < offers.size(); i++) {
            TraderPayloads.OfferInfo offer = offers.get(i);
            int rowTop = top + 70 + i * 44;
            KingdomsButton button = KingdomsButton.create(
                    Component.translatable("screen.kingdoms.trader.buy"),
                    pressed -> buy(offer.id()),
                    left + 218,
                    rowTop + 8,
                    82,
                    20
            );
            button.active = pendingOfferId.isBlank();
            addRenderableWidget(button);
        }
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 25,
                66,
                20
        ));
    }

    private void buy(String offerId) {
        if (!pendingOfferId.isBlank()) {
            return;
        }
        pendingOfferId = offerId;
        rebuildWidgets();
        PacketDistributor.sendToServer(new TraderPayloads.C2SBuy(traderId, offerId));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                PANEL_TEXTURE,
                left,
                top,
                0.0F,
                0.0F,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                PANEL_WIDTH,
                PANEL_HEIGHT
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 48, TEXT_DARK, false);
        for (int i = 0; i < offers.size(); i++) {
            renderOffer(graphics, offers.get(i), top + 70 + i * 44);
        }
        renderNotice(graphics);
    }

    private void renderOffer(
            GuiGraphics graphics,
            TraderPayloads.OfferInfo offerInfo,
            int rowTop
    ) {
        TraderOffer.byId(offerInfo.id()).ifPresent(offer -> {
            ItemStack stack = new ItemStack(offer.item());
            graphics.renderItem(stack, left + 31, rowTop + 10);
            graphics.drawString(font, stack.getHoverName(), left + 55, rowTop + 8, TEXT_DARK, false);
            graphics.drawString(
                    font,
                    formatPrice(offerInfo.price()),
                    left + 55,
                    rowTop + 22,
                    TEXT_MUTED,
                    false
            );
        });
    }

    private void renderNotice(GuiGraphics graphics) {
        if (notice == null || notice.getString().isBlank()) {
            return;
        }
        String clipped = font.plainSubstrByWidth(notice.getString(), 260);
        graphics.drawCenteredString(
                font,
                clipped,
                left + PANEL_WIDTH / 2,
                top + 166,
                successful ? TEXT_SUCCESS : TEXT_FAILURE
        );
    }

    private static Component formatPrice(long spurs) {
        long lastTwo = Math.floorMod(spurs, 100L);
        long last = Math.floorMod(spurs, 10L);
        String key;
        if (last == 1L && lastTwo != 11L) {
            key = "kingdoms.currency.spurs.one";
        } else if (last >= 2L && last <= 4L && (lastTwo < 12L || lastTwo > 14L)) {
            key = "kingdoms.currency.spurs.few";
        } else {
            key = "kingdoms.currency.spurs.many";
        }
        return Component.translatable(key, spurs);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
