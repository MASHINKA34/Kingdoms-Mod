package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import dev.ithundxr.createnumismatics.content.coins.CoinItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionTreasuryScreen extends FactionScreen {
    private static final long MAX_TYPED_AMOUNT = 999_999_999_999L;

    private EditBox amountBox;

    public FactionTreasuryScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.treasury"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        amountBox = new EditBox(
                font,
                left + CONTENT_LEFT,
                top + 82,
                150,
                20,
                text("screen.kingdoms.amount")
        );
        amountBox.setMaxLength(12);
        amountBox.setHint(text("screen.kingdoms.amount"));
        amountBox.setFilter(value -> value.matches("\\d{0,12}"));
        addRenderableWidget(amountBox);

        KingdomsButton depositAll = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.deposit_all"),
                button -> fillInventoryBalance(),
                left + CONTENT_LEFT + 156, top + 82, 92, 20
        ));

        KingdomsButton deposit = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.deposit"),
                button -> send(true),
                left + CONTENT_LEFT, top + 106, 82, 20
        ));

        KingdomsButton withdraw = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.withdraw"),
                button -> send(false),
                left + CONTENT_LEFT + 88, top + 106, 82, 20
        ));

        amountBox.setResponder(value -> {
            boolean valid = parseAmount() > 0L;
            deposit.active = valid;
            withdraw.active = valid && snapshot.isOfficer();
        });
        deposit.active = false;
        withdraw.active = false;
        depositAll.active = inventoryCoinBalance() > 0L;

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openRoot(snapshot, true, ""),
                left + 16, top + PANEL_HEIGHT - 25, 70, 20
        ));
    }

    private void send(boolean deposit) {
        long amount = parseAmount();
        if (amount <= 0L) {
            return;
        }
        if (deposit) {
            PacketDistributor.sendToServer(new FactionPayloads.C2SDepositTreasury(snapshot.tablePos(), amount));
        } else if (snapshot.isOfficer()) {
            PacketDistributor.sendToServer(new FactionPayloads.C2SWithdrawTreasury(snapshot.tablePos(), amount));
        }
    }

    private long parseAmount() {
        if (amountBox == null || amountBox.getValue().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(amountBox.getValue());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void fillInventoryBalance() {
        long balance = Math.min(inventoryCoinBalance(), MAX_TYPED_AMOUNT);
        amountBox.setValue(balance > 0L ? Long.toString(balance) : "");
    }

    private long inventoryCoinBalance() {
        if (minecraft == null || minecraft.player == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (stack.getItem() instanceof CoinItem coinItem) {
                long value = (long) coinItem.coin.value * stack.getCount();
                total = Long.MAX_VALUE - total < value ? Long.MAX_VALUE : total + value;
            }
        }
        return total;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(
                font,
                text("screen.kingdoms.treasury_balance", FactionManageScreen.currency(snapshot.treasury())),
                left + CONTENT_LEFT,
                top + 60,
                TEXT_DARK,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.amount"),
                left + CONTENT_LEFT,
                top + 72,
                TEXT_MUTED,
                false
        );
        if (!snapshot.isOfficer()) {
            graphics.drawString(
                    font,
                    text("screen.kingdoms.withdraw_hint"),
                    left + CONTENT_LEFT,
                    top + 132,
                    TEXT_HINT,
                    false
            );
        }
    }
}
