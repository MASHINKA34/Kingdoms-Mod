package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.trader.SellOffer;
import com.geydev.kalfactions.outpost.trader.TraderOffer;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
    private static final int SELL_COLUMNS = 2;
    private static final int SELL_CELL_WIDTH = 74;
    private static final int SELL_CELL_HEIGHT = 22;

    private final UUID traderId;
    private List<TraderPayloads.OfferInfo> offers;
    private List<TraderPayloads.OfferInfo> sellOffers;
    private Component notice;
    private boolean successful;
    private String pendingOfferId = "";
    private int left;
    private int top;
    private final List<SellCell> sellCells = new ArrayList<>();

    public TraderShopScreen(TraderPayloads.S2CShopState state) {
        super(Component.translatable("screen.kingdoms.trader.title"));
        traderId = state.traderId();
        offers = state.offers();
        sellOffers = state.sellOffers();
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
        sellOffers = state.sellOffers();
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
            int rowTop = top + 72 + i * 42;
            KingdomsButton button = KingdomsButton.create(
                    Component.translatable("screen.kingdoms.trader.buy"),
                    pressed -> buy(offer.id()),
                    left + 92,
                    rowTop + 6,
                    58,
                    20
            );
            button.active = pendingOfferId.isBlank();
            addRenderableWidget(button);
        }
        sellCells.clear();
        int sellLeft = left + 170;
        for (int i = 0; i < sellOffers.size(); i++) {
            int col = i % SELL_COLUMNS;
            int rowIndex = i / SELL_COLUMNS;
            int cellX = sellLeft + col * SELL_CELL_WIDTH;
            int cellY = top + 72 + rowIndex * SELL_CELL_HEIGHT;
            sellCells.add(new SellCell(sellOffers.get(i), cellX, cellY));
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

    private void sell(String offerId) {
        if (!pendingOfferId.isBlank()) {
            return;
        }
        pendingOfferId = offerId;
        PacketDistributor.sendToServer(new TraderPayloads.C2SSell(traderId, offerId));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && pendingOfferId.isBlank()) {
            for (SellCell cell : sellCells) {
                if (cell.contains(mouseX, mouseY) && ownedCount(cell.item()) > 0) {
                    sell(cell.offer().id());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.trader.sell_section"),
                left + 172,
                top + 60,
                TEXT_DARK,
                false
        );
        for (int i = 0; i < offers.size(); i++) {
            renderOffer(graphics, offers.get(i), top + 72 + i * 42);
        }
        ItemStack hovered = null;
        for (SellCell cell : sellCells) {
            boolean hover = cell.contains(mouseX, mouseY);
            renderSellCell(graphics, cell, hover);
            if (hover) {
                hovered = new ItemStack(cell.item());
            }
        }
        renderNotice(graphics);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void renderOffer(
            GuiGraphics graphics,
            TraderPayloads.OfferInfo offerInfo,
            int rowTop
    ) {
        TraderOffer.byId(offerInfo.id()).ifPresent(offer -> {
            ItemStack stack = new ItemStack(offer.item());
            graphics.renderItem(stack, left + 24, rowTop + 4);
            graphics.drawString(font, stack.getHoverName(), left + 44, rowTop + 2, TEXT_DARK, false);
            graphics.drawString(
                    font,
                    formatPrice(offerInfo.price()),
                    left + 44,
                    rowTop + 14,
                    TEXT_MUTED,
                    false
            );
        });
    }

    private void renderSellCell(GuiGraphics graphics, SellCell cell, boolean hover) {
        int owned = ownedCount(cell.item());
        if (hover && owned > 0) {
            graphics.fill(cell.x(), cell.y(), cell.x() + SELL_CELL_WIDTH - 2, cell.y() + SELL_CELL_HEIGHT - 2, 0x33000000);
        }
        ItemStack stack = new ItemStack(cell.item());
        graphics.renderItem(stack, cell.x() + 2, cell.y() + 2);
        int textColor = owned > 0 ? TEXT_DARK : 0xFF8A7A66;
        graphics.drawString(font, formatPrice(cell.offer().price()), cell.x() + 21, cell.y() + 2, textColor, false);
        graphics.drawString(font, "x" + owned, cell.x() + 21, cell.y() + 11, TEXT_MUTED, false);
    }

    private int ownedCount(Item item) {
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void renderNotice(GuiGraphics graphics) {
        if (notice == null || notice.getString().isBlank()) {
            return;
        }
        String clipped = font.plainSubstrByWidth(notice.getString(), PANEL_WIDTH - 90);
        graphics.drawString(
                font,
                clipped,
                left + 24,
                top + PANEL_HEIGHT - 20,
                successful ? TEXT_SUCCESS : TEXT_FAILURE,
                false
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

    private record SellCell(TraderPayloads.OfferInfo offer, int x, int y) {
        private Item item() {
            return SellOffer.byId(offer.id()).map(SellOffer::item).orElse(net.minecraft.world.item.Items.AIR);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + SELL_CELL_WIDTH - 2
                    && mouseY >= y && mouseY < y + SELL_CELL_HEIGHT - 2;
        }
    }
}
