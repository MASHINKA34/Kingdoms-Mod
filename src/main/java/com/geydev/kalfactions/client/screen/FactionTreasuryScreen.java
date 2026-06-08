package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionTreasuryScreen extends FactionScreen {
    private EditBox amountBox;

    public FactionTreasuryScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.treasury", "Treasury"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        amountBox = new EditBox(
                font,
                left + 16,
                top + 76,
                150,
                20,
                text("screen.kingdoms.amount", "Amount")
        );
        amountBox.setMaxLength(12);
        amountBox.setHint(text("screen.kingdoms.amount", "Amount"));
        amountBox.setFilter(value -> value.matches("\\d{0,12}"));
        addRenderableWidget(amountBox);

        Button deposit = addRenderableWidget(Button.builder(
                text("screen.kingdoms.deposit", "Deposit"),
                button -> send(true)
        ).bounds(left + 172, top + 76, 66, 20).build());

        Button withdraw = addRenderableWidget(Button.builder(
                text("screen.kingdoms.withdraw", "Withdraw"),
                button -> send(false)
        ).bounds(left + 242, top + 76, 74, 20).build());

        amountBox.setResponder(value -> {
            boolean valid = parseAmount() > 0L;
            deposit.active = valid;
            withdraw.active = valid && snapshot.isOfficer();
        });
        deposit.active = false;
        withdraw.active = false;

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.back", "Back"),
                button -> FactionScreens.openRoot(snapshot, true, "")
        ).bounds(left + 16, top + PANEL_HEIGHT - 25, 70, 20).build());
        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh", "Refresh"),
                button -> requestRefresh()
        ).bounds(left + 90, top + PANEL_HEIGHT - 25, 70, 20).build());
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(
                font,
                text("screen.kingdoms.treasury", "Treasury").copy()
                        .append(": " + FactionManageScreen.spurs(snapshot.treasury())),
                left + 16,
                top + 44,
                0x3F2A19,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.amount", "Amount"),
                left + 16,
                top + 64,
                0x4C3824,
                false
        );
        if (!snapshot.isOfficer()) {
            graphics.drawString(
                    font,
                    text("screen.kingdoms.withdraw_hint", "Only officers and the leader may withdraw."),
                    left + 16,
                    top + 104,
                    0x5B452E,
                    false
            );
        }
    }
}
