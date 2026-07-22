package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.KingdomsNoticeToast;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TraderShopScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int OFFER_TOP = 68;
    private static final int OFFER_STEP = 24;
    private static final int VISIBLE_OFFERS = 5;

    private final UUID traderId;
    private UUID sessionId;
    private long nextSequence;
    private List<TraderPayloads.OfferInfo> offers;
    private String pendingOfferId = "";
    private int scroll;
    private int left;
    private int top;

    public TraderShopScreen(TraderPayloads.S2CShopState state) {
        super(Component.translatable(state.titleKey()));
        traderId = state.traderId();
        sessionId = state.sessionId();
        nextSequence = Math.max(1L, state.acknowledgedSequence() + 1L);
        offers = state.offers();
    }

    public static void handle(TraderPayloads.S2CShopState state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (state.offers().isEmpty() && "screen.kingdoms.seller.title".equals(state.titleKey())) {
                SellerShopScreen.handle(state);
            } else if (minecraft.screen instanceof TraderShopScreen screen
                    && screen.traderId.equals(state.traderId())) {
                screen.acceptState(state);
            } else {
                showShopNotice(state.notice(), state.successful());
                minecraft.setScreen(new TraderShopScreen(state));
            }
        });
    }

    private void acceptState(TraderPayloads.S2CShopState state) {
        sessionId = state.sessionId();
        nextSequence = Math.max(nextSequence, state.acknowledgedSequence() + 1L);
        offers = state.offers();
        pendingOfferId = "";
        scroll = Math.clamp(scroll, 0, maxScroll());
        showShopNotice(state.notice(), state.successful());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        scroll = Math.clamp(scroll, 0, maxScroll());
        int shown = Math.min(VISIBLE_OFFERS, offers.size() - scroll);
        for (int i = 0; i < shown; i++) {
            TraderPayloads.OfferInfo offer = offers.get(scroll + i);
            int rowTop = top + OFFER_TOP + i * OFFER_STEP;
            KingdomsButton button = KingdomsButton.create(
                    Component.translatable("screen.kingdoms.trader.buy"),
                    pressed -> buy(offer.id()),
                    left + PANEL_WIDTH - 92,
                    rowTop + 3,
                    68,
                    18
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
        PacketDistributor.sendToServer(new TraderPayloads.C2SBuy(traderId, sessionId, nextSequence++, offerId));
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new TraderPayloads.C2SCloseTrader(traderId, sessionId));
        super.onClose();
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
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.trader.buy_section"),
                left + 24,
                top + 60,
                TEXT_DARK,
                false
        );
        int shown = Math.min(VISIBLE_OFFERS, offers.size() - scroll);
        if (offers.size() > VISIBLE_OFFERS) {
            String pager = (scroll + 1) + "-" + (scroll + shown) + " / " + offers.size();
            graphics.drawString(font, pager, left + PANEL_WIDTH - 32 - font.width(pager), top + 60, TEXT_MUTED, false);
        }
        for (int i = 0; i < shown; i++) {
            renderOffer(graphics, offers.get(scroll + i), top + OFFER_TOP + i * OFFER_STEP);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int updated = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        if (updated != scroll) {
            scroll = updated;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScroll() {
        return Math.max(0, offers.size() - VISIBLE_OFFERS);
    }

    private void renderOffer(
            GuiGraphics graphics,
            TraderPayloads.OfferInfo offerInfo,
            int rowTop
    ) {
        ResourceLocation itemId = ResourceLocation.tryParse(offerInfo.itemId());
        if (itemId != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), offerInfo.itemCount());
            graphics.renderItem(stack, left + 24, rowTop + 4);
            Component name = Component.literal(font.plainSubstrByWidth(
                    stack.getHoverName().getString(),
                    PANEL_WIDTH - 150
            ));
            graphics.drawString(font, name, left + 44, rowTop + 1, TEXT_DARK, false);
            graphics.drawString(
                    font,
                    formatPrice(offerInfo.price()),
                    left + 44,
                    rowTop + 12,
                    TEXT_MUTED,
                    false
            );
        }
    }

    static Component formatPrice(long spurs) {
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

    private static void showShopNotice(Component notice, boolean successful) {
        KingdomsNoticeToast.show(notice, successful);
    }

}
