package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.client.KingdomsNoticeToast;
import com.geydev.kalfactions.economy.PriceMath;
import com.geydev.kalfactions.outpost.trader.SellOffer;
import com.geydev.kalfactions.outpost.trader.TraderPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
    private static final int SELL_CELL_HEIGHT = 24;

    private final UUID traderId;
    private List<TraderPayloads.OfferInfo> sellOffers;
    private long nextRefreshEpochMillis;
    private long lastRefreshRequestEpochMillis;
    private String pendingOfferId = "";
    private int left;
    private int top;
    private final List<SellCell> sellCells = new ArrayList<>();
    private String selectedOfferId = "";
    private SellCell selectedCell;
    private EditBox amountBox;
    private KingdomsButton maxButton;
    private KingdomsButton sellButton;

    public SellerShopScreen(TraderPayloads.S2CShopState state) {
        super(Component.translatable("screen.kingdoms.seller.title"));
        traderId = state.traderId();
        sellOffers = state.sellOffers();
        nextRefreshEpochMillis = state.nextSellRefreshEpochMillis();
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
        nextRefreshEpochMillis = state.nextSellRefreshEpochMillis();
        pendingOfferId = "";
        selectedOfferId = "";
        selectedCell = null;
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
            int cellY = top + 80 + rowIndex * SELL_CELL_HEIGHT;
            sellCells.add(new SellCell(sellOffers.get(i), cellX, cellY));
        }
        selectedCell = findSelectedCell();
        amountBox = new EditBox(
                font,
                left + 35,
                top + 166,
                58,
                20,
                Component.translatable("screen.kingdoms.seller.amount")
        );
        amountBox.setMaxLength(6);
        amountBox.setHint(Component.translatable("screen.kingdoms.seller.amount"));
        amountBox.setFilter(value -> value.matches("\\d{0,6}"));
        amountBox.setResponder(value -> updateSellControls());
        if (selectedCell != null) {
            int amount = Math.min(maxSellable(selectedCell), Math.max(1, requestedAmount()));
            amountBox.setValue(amount > 0 ? Integer.toString(amount) : "");
        }
        addRenderableWidget(amountBox);
        maxButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.seller.max"),
                button -> fillMaxAmount(),
                left + 98,
                top + 166,
                46,
                20
        ));
        sellButton = addRenderableWidget(KingdomsButton.create(
                Component.translatable("screen.kingdoms.seller.sell"),
                button -> sellSelected(),
                left + 149,
                top + 166,
                70,
                20
        ));
        updateSellControls();
        addRenderableWidget(KingdomsButton.create(
                Component.translatable("gui.done"),
                button -> onClose(),
                left + PANEL_WIDTH - 74,
                top + PANEL_HEIGHT - 25,
                66,
                20
        ));
    }

    private void sell(String offerId, int amount) {
        if (!pendingOfferId.isBlank()) {
            return;
        }
        pendingOfferId = offerId;
        updateSellControls();
        PacketDistributor.sendToServer(new TraderPayloads.C2SSell(traderId, offerId, amount));
    }

    @Override
    public void tick() {
        super.tick();
        if (nextRefreshEpochMillis > 0L
                && remainingRefreshSeconds() <= 0L
                && lastRefreshRequestEpochMillis != nextRefreshEpochMillis) {
            lastRefreshRequestEpochMillis = nextRefreshEpochMillis;
            PacketDistributor.sendToServer(new TraderPayloads.C2SRefreshSeller(traderId));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && pendingOfferId.isBlank()) {
            for (SellCell cell : sellCells) {
                if (cell.contains(mouseX, mouseY)) {
                    selectCell(cell);
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
        graphics.drawString(font, title, left + (PANEL_WIDTH - font.width(title)) / 2, top + 42, TEXT_DARK, false);
        Component timer = Component.translatable(
                "screen.kingdoms.seller.refresh_timer",
                formatDuration(remainingRefreshSeconds())
        );
        graphics.drawString(font, timer, left + (PANEL_WIDTH - font.width(timer)) / 2, top + 57, TEXT_MUTED, false);
        graphics.drawString(font, Component.translatable("screen.kingdoms.trader.sell_section"), left + 35, top + 69, TEXT_MUTED, false);
        ItemStack hovered = null;
        for (SellCell cell : sellCells) {
            boolean hover = cell.contains(mouseX, mouseY);
            renderSellCell(graphics, cell, hover);
            if (hover) {
                hovered = new ItemStack(cell.item());
            }
        }
        renderSelectionInfo(graphics);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private long remainingRefreshSeconds() {
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

    private void renderSellCell(GuiGraphics graphics, SellCell cell, boolean hover) {
        int owned = ownedCount(cell.item());
        int available = maxSellable(cell);
        boolean selected = cell.offer().id().equals(selectedOfferId);
        int background = selected ? 0x88D1A43D : hover && available > 0 ? 0x66C9A24C : 0x24A8783D;
        graphics.fill(cell.x(), cell.y(), cell.x() + SELL_CELL_WIDTH - 4, cell.y() + SELL_CELL_HEIGHT - 2, background);
        if (selected) {
            graphics.renderOutline(cell.x(), cell.y(), SELL_CELL_WIDTH - 4, SELL_CELL_HEIGHT - 2, 0xFFD8B25A);
        }
        ItemStack stack = new ItemStack(cell.item());
        graphics.renderItem(stack, cell.x() + 3, cell.y() + 4);
        int textColor = available > 0 ? TEXT_DARK : 0xFF8A7A66;
        graphics.drawString(font, TraderShopScreen.formatPrice(cell.offer().price()), cell.x() + 23, cell.y() + 3, textColor, false);
        graphics.drawString(font, "x" + owned, cell.x() + 23, cell.y() + 13, available > 0 ? 0xFFC9921F : TEXT_MUTED, false);
    }

    private void renderSelectionInfo(GuiGraphics graphics) {
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.seller.amount"),
                left + 35,
                top + 156,
                TEXT_MUTED,
                false
        );
        if (selectedCell == null) {
            graphics.drawString(
                    font,
                    Component.translatable("screen.kingdoms.seller.pick"),
                    left + 224,
                    top + 171,
                    TEXT_MUTED,
                    false
            );
            return;
        }
        int available = maxSellable(selectedCell);
        graphics.drawString(
                font,
                Component.translatable("screen.kingdoms.seller.available", available),
                left + 224,
                top + 171,
                available > 0 ? TEXT_MUTED : 0xFF8A5A45,
                false
        );
        int amount = requestedAmount();
        if (amount > 0 && amount <= available) {
            long payout = PriceMath.saturatedMultiply(selectedCell.offer().price(), amount);
            graphics.drawString(
                    font,
                    Component.translatable("screen.kingdoms.seller.payout", TraderShopScreen.formatPrice(payout)),
                    left + 35,
                    top + 189,
                    TEXT_DARK,
                    false
            );
        }
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

    private int maxSellable(SellCell cell) {
        return Math.min(ownedCount(cell.item()), Math.max(0, cell.offer().remainingLimit()));
    }

    private void selectCell(SellCell cell) {
        selectedOfferId = cell.offer().id();
        selectedCell = cell;
        fillMaxAmount();
        updateSellControls();
    }

    private SellCell findSelectedCell() {
        if (selectedOfferId.isBlank()) {
            return null;
        }
        for (SellCell cell : sellCells) {
            if (cell.offer().id().equals(selectedOfferId)) {
                return cell;
            }
        }
        selectedOfferId = "";
        return null;
    }

    private void fillMaxAmount() {
        if (amountBox == null || selectedCell == null) {
            return;
        }
        int amount = maxSellable(selectedCell);
        amountBox.setValue(amount > 0 ? Integer.toString(amount) : "");
    }

    private void sellSelected() {
        if (selectedCell == null) {
            return;
        }
        int amount = requestedAmount();
        if (amount <= 0 || amount > maxSellable(selectedCell)) {
            updateSellControls();
            return;
        }
        sell(selectedCell.offer().id(), amount);
    }

    private int requestedAmount() {
        if (amountBox == null || amountBox.getValue().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(amountBox.getValue());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void updateSellControls() {
        int max = selectedCell == null ? 0 : maxSellable(selectedCell);
        int amount = requestedAmount();
        boolean canEdit = selectedCell != null && pendingOfferId.isBlank() && max > 0;
        if (amountBox != null) {
            amountBox.active = canEdit;
        }
        if (maxButton != null) {
            maxButton.active = canEdit;
        }
        if (sellButton != null) {
            sellButton.active = canEdit && amount > 0 && amount <= max;
        }
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
