package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.trader.SellOffer;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SellerCatalogScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int SELLER_ROWS = 5;
    private static final int SELLER_ROW_HEIGHT = 22;
    private static final int OFFER_COLUMNS = 2;
    private static final int OFFER_ROWS = 4;
    private static final int OFFER_CELL_WIDTH = 78;
    private static final int OFFER_CELL_HEIGHT = 26;

    private List<TraderPayloads.SellerInfo> sellers;
    private int selectedIndex;
    private int sellerScroll;
    private int offerScroll;
    private int left;
    private int top;

    public SellerCatalogScreen(TraderPayloads.S2CSellerCatalog catalog) {
        super(Component.translatable("screen.kingdoms.seller_catalog.title"));
        sellers = catalog.sellers();
    }

    public static void handle(TraderPayloads.S2CSellerCatalog catalog) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof SellerCatalogScreen screen) {
                screen.acceptCatalog(catalog);
            } else {
                minecraft.setScreen(new SellerCatalogScreen(catalog));
            }
        });
    }

    private void acceptCatalog(TraderPayloads.S2CSellerCatalog catalog) {
        sellers = catalog.sellers();
        selectedIndex = Math.clamp(selectedIndex, 0, Math.max(0, sellers.size() - 1));
        sellerScroll = Math.clamp(sellerScroll, 0, maxSellerScroll());
        offerScroll = Math.clamp(offerScroll, 0, maxOfferScroll());
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        selectedIndex = Math.clamp(selectedIndex, 0, Math.max(0, sellers.size() - 1));
        sellerScroll = Math.clamp(sellerScroll, 0, maxSellerScroll());
        offerScroll = Math.clamp(offerScroll, 0, maxOfferScroll());
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 25,
                66,
                20
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
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 42, TEXT_DARK, false);
        if (sellers.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.seller_catalog.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 98, TEXT_MUTED, false);
            return;
        }

        graphics.drawString(font, Component.translatable("screen.kingdoms.seller_catalog.sellers"),
                left + 24, top + 61, TEXT_MUTED, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.seller_catalog.offers"),
                left + 146, top + 61, TEXT_MUTED, false);
        renderSellers(graphics, mouseX, mouseY);
        renderOffers(graphics, mouseX, mouseY);
    }

    private void renderSellers(GuiGraphics graphics, int mouseX, int mouseY) {
        int shown = Math.min(SELLER_ROWS, sellers.size() - sellerScroll);
        for (int i = 0; i < shown; i++) {
            int index = sellerScroll + i;
            int x = left + 24;
            int y = top + 75 + i * SELLER_ROW_HEIGHT;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= x && mouseX < x + 108 && mouseY >= y && mouseY < y + 18;
            graphics.fill(x, y, x + 108, y + 18, selected ? 0x88D1A43D : hovered ? 0x55C9A24C : 0x24A8783D);
            if (selected) {
                graphics.renderOutline(x, y, 108, 18, 0xFFD8B25A);
            }
            String label = font.plainSubstrByWidth(sellers.get(index).label(), 100);
            graphics.drawString(font, label, x + 4, y + 5, selected ? TEXT_DARK : TEXT_MUTED, false);
        }
        if (sellers.size() > SELLER_ROWS) {
            String pager = (sellerScroll + 1) + "-" + (sellerScroll + shown) + " / " + sellers.size();
            graphics.drawString(font, pager, left + 24, top + 188, TEXT_MUTED, false);
        }
    }

    private void renderOffers(GuiGraphics graphics, int mouseX, int mouseY) {
        TraderPayloads.SellerInfo seller = selectedSeller();
        if (seller == null) {
            return;
        }
        Component timer = Component.translatable(
                "screen.kingdoms.seller.refresh_timer",
                formatDuration(remainingRefreshSeconds(seller.nextRefreshEpochMillis()))
        );
        graphics.drawString(font, timer, left + 146, top + 75, TEXT_MUTED, false);
        List<TraderPayloads.OfferInfo> offers = seller.offers();
        int shown = Math.min(OFFER_COLUMNS * OFFER_ROWS, offers.size() - offerScroll);
        ItemStack hovered = null;
        for (int i = 0; i < shown; i++) {
            int index = offerScroll + i;
            int col = i % OFFER_COLUMNS;
            int row = i / OFFER_COLUMNS;
            int x = left + 146 + col * OFFER_CELL_WIDTH;
            int y = top + 91 + row * OFFER_CELL_HEIGHT;
            TraderPayloads.OfferInfo offer = offers.get(index);
            ItemStack stack = SellOffer.byId(offer.id())
                    .map(found -> new ItemStack(found.item()))
                    .orElse(new ItemStack(Items.AIR));
            boolean active = offer.remainingLimit() > 0;
            boolean mouseOver = mouseX >= x && mouseX < x + OFFER_CELL_WIDTH - 4
                    && mouseY >= y && mouseY < y + OFFER_CELL_HEIGHT - 2;
            graphics.fill(x, y, x + OFFER_CELL_WIDTH - 4, y + OFFER_CELL_HEIGHT - 2,
                    mouseOver ? 0x55C9A24C : 0x24A8783D);
            graphics.renderItem(stack, x + 3, y + 5);
            graphics.drawString(font, TraderShopScreen.formatPrice(offer.price()), x + 23, y + 3,
                    active ? TEXT_DARK : 0xFF8A7A66, false);
            graphics.drawString(font, Component.translatable("screen.kingdoms.seller_catalog.limit", offer.remainingLimit()),
                    x + 23, y + 14, active ? TEXT_MUTED : 0xFF8A5A45, false);
            if (mouseOver) {
                hovered = stack;
            }
        }
        if (offers.size() > OFFER_COLUMNS * OFFER_ROWS) {
            String pager = (offerScroll + 1) + "-" + (offerScroll + shown) + " / " + offers.size();
            graphics.drawString(font, pager, left + 146, top + 188, TEXT_MUTED, false);
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private TraderPayloads.SellerInfo selectedSeller() {
        if (selectedIndex < 0 || selectedIndex >= sellers.size()) {
            return null;
        }
        return sellers.get(selectedIndex);
    }

    private long remainingRefreshSeconds(long nextRefreshEpochMillis) {
        if (nextRefreshEpochMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (nextRefreshEpochMillis - System.currentTimeMillis() + 999L) / 1_000L);
    }

    private static String formatDuration(long seconds) {
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainingSeconds = seconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !sellers.isEmpty()) {
            int shown = Math.min(SELLER_ROWS, sellers.size() - sellerScroll);
            for (int i = 0; i < shown; i++) {
                int x = left + 24;
                int y = top + 75 + i * SELLER_ROW_HEIGHT;
                if (mouseX >= x && mouseX < x + 108 && mouseY >= y && mouseY < y + 18) {
                    selectedIndex = sellerScroll + i;
                    offerScroll = 0;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (sellers.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (mouseX < left + 140) {
            int updated = Math.clamp(sellerScroll - (int) Math.signum(scrollY), 0, maxSellerScroll());
            if (updated != sellerScroll) {
                sellerScroll = updated;
                return true;
            }
        } else {
            int updated = Math.clamp(offerScroll - (int) Math.signum(scrollY), 0, maxOfferScroll());
            if (updated != offerScroll) {
                offerScroll = updated;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxSellerScroll() {
        return Math.max(0, sellers.size() - SELLER_ROWS);
    }

    private int maxOfferScroll() {
        TraderPayloads.SellerInfo seller = selectedSeller();
        return seller == null ? 0 : Math.max(0, seller.offers().size() - OFFER_COLUMNS * OFFER_ROWS);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
