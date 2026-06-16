package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.KingdomsNoticeToast;
import com.geydev.kalfactions.outpost.trader.SellOffer;
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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SellerShopScreen extends Screen {
    private static final ResourceLocation PANEL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(KalFactions.MOD_ID, "textures/gui/faction/panel.png");
    private static final int PANEL_WIDTH = 330;
    private static final int PANEL_HEIGHT = 220;
    private static final int TEXT_DARK = 0xFF3F2A19;
    private static final int TEXT_MUTED = 0xFF5B452E;
    private static final int SELL_COLUMNS = 3;
    private static final int SELL_CELL_WIDTH = 82;
    private static final int SELL_CELL_HEIGHT = 25;

    private final UUID traderId;
    private List<TraderPayloads.OfferInfo> sellOffers;
    private String pendingOfferId = "";
    private int left;
    private int top;
    private final List<SellCell> sellCells = new ArrayList<>();

    public SellerShopScreen(TraderPayloads.S2CShopState state) {
        super(Component.translatable("screen.kingdoms.seller.title"));
        traderId = state.traderId();
        sellOffers = state.sellOffers();
    }

    public static void handle(TraderPayloads.S2CShopState state) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.screen instanceof SellerShopScreen screen
                    && screen.traderId.equals(state.traderId())) {
                screen.acceptState(state);
            } else {
                showShopNotice(state.notice(), state.successful());
                minecraft.setScreen(new SellerShopScreen(state));
            }
        });
    }

    private void acceptState(TraderPayloads.S2CShopState state) {
        sellOffers = state.sellOffers();
        pendingOfferId = "";
        showShopNotice(state.notice(), state.successful());
        rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        sellCells.clear();
        int sellLeft = left + 35;
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
        graphics.blit(PANEL_TEXTURE, left, top, 0.0F, 0.0F, PANEL_WIDTH, PANEL_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 48, TEXT_DARK, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.trader.sell_section"), left + 35, top + 60, TEXT_MUTED, false);
        ItemStack hovered = null;
        for (SellCell cell : sellCells) {
            boolean hover = cell.contains(mouseX, mouseY);
            renderSellCell(graphics, cell, hover);
            if (hover) {
                hovered = new ItemStack(cell.item());
            }
        }
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void renderSellCell(GuiGraphics graphics, SellCell cell, boolean hover) {
        int owned = ownedCount(cell.item());
        int background = hover && owned > 0 ? 0x66C9A24C : 0x24A8783D;
        graphics.fill(cell.x(), cell.y(), cell.x() + SELL_CELL_WIDTH - 4, cell.y() + SELL_CELL_HEIGHT - 2, background);
        ItemStack stack = new ItemStack(cell.item());
        graphics.renderItem(stack, cell.x() + 3, cell.y() + 4);
        int textColor = owned > 0 ? TEXT_DARK : 0xFF8A7A66;
        graphics.drawString(font, TraderShopScreen.formatPrice(cell.offer().price()), cell.x() + 23, cell.y() + 3, textColor, false);
        graphics.drawString(font, "x" + owned, cell.x() + 23, cell.y() + 13, owned > 0 ? 0xFFC9921F : TEXT_MUTED, false);
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void showShopNotice(Component notice, boolean successful) {
        KingdomsNoticeToast.show(notice, successful);
    }

    private record SellCell(TraderPayloads.OfferInfo offer, int x, int y) {
        private Item item() {
            return SellOffer.byId(offer.id()).map(SellOffer::item).orElse(Items.AIR);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + SELL_CELL_WIDTH - 4
                    && mouseY >= y && mouseY < y + SELL_CELL_HEIGHT - 2;
        }
    }
}
