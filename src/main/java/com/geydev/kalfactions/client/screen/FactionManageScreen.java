package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionManageScreen extends FactionScreen {
    private EditBox nameBox;
    private int selectedColor;

    public FactionManageScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.faction_manage"), snapshot, successful, message);
        selectedColor = snapshot.color();
    }

    @Override
    protected void initFactionWidgets() {
        int leftColumn = left + 16;
        int rightColumn = left + 170;
        int columnWidth = 150;
        int row0 = top + 76;
        int rowStep = 24;

        nameBox = new EditBox(
                font,
                leftColumn,
                row0,
                columnWidth,
                20,
                text("screen.kingdoms.faction_name")
        );
        nameBox.setMaxLength(32);
        nameBox.setValue(snapshot.name());
        nameBox.setEditable(snapshot.canManage());
        addRenderableWidget(nameBox);

        Button color = addRenderableWidget(Button.builder(
                text("screen.kingdoms.color"),
                button -> selectedColor = nextColor(selectedColor)
        ).bounds(leftColumn, row0 + rowStep, columnWidth, 20).build());
        color.active = snapshot.canManage();

        Button save = addRenderableWidget(Button.builder(
                text("screen.kingdoms.save"),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SUpdateFaction(snapshot.tablePos(), nameBox.getValue(), selectedColor)
                )
        ).bounds(leftColumn, row0 + rowStep * 2, columnWidth, 20).build());
        save.active = snapshot.canManage();

        Button pvp = addRenderableWidget(Button.builder(
                pvpLabel(),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SSetPvp(snapshot.tablePos(), !snapshot.internalPvp())
                )
        ).bounds(leftColumn, row0 + rowStep * 3, columnWidth, 20).build());
        pvp.active = snapshot.isOfficer();

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.members_count", snapshot.members().size()),
                button -> FactionScreens.openMembers(snapshot, true, "")
        ).bounds(rightColumn, row0, columnWidth, 20).build());

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.treasury"),
                button -> FactionScreens.openTreasury(snapshot, true, "")
        ).bounds(rightColumn, row0 + rowStep, columnWidth, 20).build());

        Button claims = addRenderableWidget(Button.builder(
                text("screen.kingdoms.claim_map.open"),
                button -> FactionScreens.openClaims(snapshot, true, "")
        ).bounds(rightColumn, row0 + rowStep * 2, columnWidth, 20).build());
        claims.active = snapshot.canClaim();

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh"),
                button -> requestRefresh()
        ).bounds(rightColumn, row0 + rowStep * 3, columnWidth, 20).build());
    }

    private Component pvpLabel() {
        Component state = snapshot.internalPvp()
                ? text("screen.kingdoms.on")
                : text("screen.kingdoms.off");
        return text("screen.kingdoms.pvp", state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left + 16, top + 33, left + 28, top + 45, 0xFF000000 | selectedColor);
        graphics.drawString(font, snapshot.name(), left + 33, top + 35, 0x3F2A19, false);

        graphics.drawString(
                font,
                text("screen.kingdoms.owner", snapshot.ownerName()),
                left + 16,
                top + 51,
                0x4C3824,
                false
        );
        graphics.drawString(
                font,
                pvpLabel(),
                left + 200,
                top + 51,
                0x4C3824,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.treasury_balance", currency(snapshot.treasury())),
                left + 16,
                top + 63,
                0x4C3824,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.influence_value", snapshot.influence()),
                left + 200,
                top + 63,
                0x4C3824,
                false
        );
    }

    static Component currency(long amount) {
        long lastTwo = Math.floorMod(amount, 100L);
        long last = Math.floorMod(amount, 10L);
        String key;
        if (last == 1L && lastTwo != 11L) {
            key = "kingdoms.currency.spurs.one";
        } else if (last >= 2L && last <= 4L && (lastTwo < 12L || lastTwo > 14L)) {
            key = "kingdoms.currency.spurs.few";
        } else {
            key = "kingdoms.currency.spurs.many";
        }
        return text(key, amount);
    }
}
