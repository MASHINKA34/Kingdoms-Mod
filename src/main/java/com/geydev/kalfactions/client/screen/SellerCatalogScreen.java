package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SellerCatalogScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONTENT_LEFT = 28;
    private static final int CONTENT_RIGHT = 298;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int SELLER_ROWS = 5;
    private static final int SELLER_ROW_HEIGHT = 22;
    private static final int SELLER_LIST_TOP = 70;
    private static final int OFFER_COLUMNS = 3;
    private static final int OFFER_ROWS = 3;
    private static final int OFFER_CELL_WIDTH = 90;
    private static final int OFFER_CELL_HEIGHT = 30;
    private static final int OFFER_GRID_TOP = 88;

    private List<TraderPayloads.SellerInfo> sellers;
    private UUID openedSellerId;
    private int sellerScroll;
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
        if (openedSeller() == null) {
            openedSellerId = null;
        }
        sellerScroll = Math.clamp(sellerScroll, 0, maxSellerScroll());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        sellerScroll = Math.clamp(sellerScroll, 0, maxSellerScroll());
        if (openedSellerId != null) {
            addRenderableWidget(KingdomsButton.create(
                    Component.translatable("screen.kingdoms.back"),
                    button -> {
                        openedSellerId = null;
                        rebuildWidgets();
                    },
                    left + CONTENT_LEFT,
                    top + PANEL_HEIGHT - 25,
                    66,
                    20
            ));
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 48, TEXT_DARK, false);
        TraderPayloads.SellerInfo opened = openedSeller();
        if (opened != null) {
            renderOffersView(graphics, opened, mouseX, mouseY);
        } else {
            renderSellerList(graphics, mouseX, mouseY);
        }
    }

    private void renderSellerList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sellers.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.seller_catalog.empty");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(SELLER_ROWS, sellers.size() - sellerScroll);
        for (int i = 0; i < shown; i++) {
            TraderPayloads.SellerInfo seller = sellers.get(sellerScroll + i);
            int x = left + CONTENT_LEFT;
            int y = top + SELLER_LIST_TOP + i * SELLER_ROW_HEIGHT;
            int right = left + CONTENT_RIGHT;
            boolean hovered = mouseX >= x && mouseX < right && mouseY >= y && mouseY < y + SELLER_ROW_HEIGHT - 2;
            graphics.fill(x, y, right, y + SELLER_ROW_HEIGHT - 2, hovered ? 0x55C9A24C : 0x24A8783D);
            graphics.drawString(font, sellerName(seller), x + 6, y + 6, TEXT_DARK, false);
            Component count = Component.translatable("screen.kingdoms.seller_catalog.offers_count", seller.offers().size());
            graphics.drawString(font, count, right - 6 - font.width(count), y + 6, TEXT_MUTED, false);
        }
        if (sellers.size() > SELLER_ROWS) {
            String pager = (sellerScroll + 1) + "-" + (sellerScroll + shown) + " / " + sellers.size();
            graphics.drawString(font, pager, left + CONTENT_RIGHT - font.width(pager), top + 62, TEXT_MUTED, false);
        }
    }

    private void renderOffersView(GuiGraphics graphics, TraderPayloads.SellerInfo seller, int mouseX, int mouseY) {
        graphics.drawString(font, sellerName(seller), left + CONTENT_LEFT, top + 62, TEXT_DARK, false);
        Component timer = Component.translatable(
                "screen.kingdoms.seller.refresh_timer",
                formatDuration(remainingRefreshSeconds(seller.nextRefreshEpochMillis()))
        );
        graphics.drawString(font, timer, left + CONTENT_RIGHT - font.width(timer), top + 62, TEXT_MUTED, false);

        List<TraderPayloads.OfferInfo> offers = seller.offers();
        if (offers.isEmpty()) {
            Component empty = Component.translatable("screen.kingdoms.seller_catalog.empty_offers");
            graphics.drawString(font, empty, left + (PANEL_WIDTH - font.width(empty)) / 2, top + 110, TEXT_MUTED, false);
            return;
        }
        int shown = Math.min(OFFER_COLUMNS * OFFER_ROWS, offers.size());
        ItemStack hovered = null;
        for (int i = 0; i < shown; i++) {
            int col = i % OFFER_COLUMNS;
            int row = i / OFFER_COLUMNS;
            int x = left + CONTENT_LEFT + col * OFFER_CELL_WIDTH;
            int y = top + OFFER_GRID_TOP + row * OFFER_CELL_HEIGHT;
            TraderPayloads.OfferInfo offer = offers.get(i);
            ResourceLocation itemId = ResourceLocation.tryParse(offer.itemId());
            ItemStack stack = itemId == null
                    ? new ItemStack(Items.AIR)
                    : new ItemStack(BuiltInRegistries.ITEM.get(itemId));
            boolean active = offer.remainingLimit() > 0;
            boolean mouseOver = mouseX >= x && mouseX < x + OFFER_CELL_WIDTH - 4
                    && mouseY >= y && mouseY < y + OFFER_CELL_HEIGHT - 4;
            graphics.fill(x, y, x + OFFER_CELL_WIDTH - 4, y + OFFER_CELL_HEIGHT - 4,
                    mouseOver ? 0x55C9A24C : 0x24A8783D);
            graphics.renderItem(stack, x + 4, y + 5);
            graphics.drawString(font, TraderShopScreen.formatPrice(offer.price()), x + 25, y + 4,
                    active ? TEXT_DARK : 0xFF8A7A66, false);
            graphics.drawString(font,
                    Component.translatable("screen.kingdoms.seller_catalog.limit", offer.remainingLimit()),
                    x + 25, y + 15, active ? TEXT_MUTED : 0xFF8A5A45, false);
            if (mouseOver) {
                hovered = stack;
            }
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private Component sellerName(TraderPayloads.SellerInfo seller) {
        return Component.translatable("screen.kingdoms.seller_catalog.seller", seller.index());
    }

    private TraderPayloads.SellerInfo openedSeller() {
        if (openedSellerId == null) {
            return null;
        }
        return sellers.stream()
                .filter(seller -> seller.sellerId().equals(openedSellerId))
                .findFirst()
                .orElse(null);
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
        if (button == 0 && openedSellerId == null && !sellers.isEmpty()) {
            int shown = Math.min(SELLER_ROWS, sellers.size() - sellerScroll);
            for (int i = 0; i < shown; i++) {
                int x = left + CONTENT_LEFT;
                int y = top + SELLER_LIST_TOP + i * SELLER_ROW_HEIGHT;
                if (mouseX >= x && mouseX < left + CONTENT_RIGHT
                        && mouseY >= y && mouseY < y + SELLER_ROW_HEIGHT - 2) {
                    openedSellerId = sellers.get(sellerScroll + i).sellerId();
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (openedSellerId == null && !sellers.isEmpty()) {
            int updated = Math.clamp(sellerScroll - (int) Math.signum(scrollY), 0, maxSellerScroll());
            if (updated != sellerScroll) {
                sellerScroll = updated;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && openedSellerId != null) {
            openedSellerId = null;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int maxSellerScroll() {
        return Math.max(0, sellers.size() - SELLER_ROWS);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
