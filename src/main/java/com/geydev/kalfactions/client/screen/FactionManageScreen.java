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
        super(text("screen.kingdoms.faction_manage", "Faction Management"), snapshot, successful, message);
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
                text("screen.kingdoms.faction_name", "Faction name")
        );
        nameBox.setMaxLength(32);
        nameBox.setValue(snapshot.name());
        nameBox.setEditable(snapshot.canManage());
        addRenderableWidget(nameBox);

        Button color = addRenderableWidget(Button.builder(
                text("screen.kingdoms.color", "Change color"),
                button -> selectedColor = nextColor(selectedColor)
        ).bounds(leftColumn, row0 + rowStep, columnWidth, 20).build());
        color.active = snapshot.canManage();

        Button save = addRenderableWidget(Button.builder(
                text("screen.kingdoms.save", "Save changes"),
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
                text("screen.kingdoms.members", "Members").copy()
                        .append(" (" + snapshot.members().size() + ")"),
                button -> FactionScreens.openMembers(snapshot, true, "")
        ).bounds(rightColumn, row0, columnWidth, 20).build());

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.treasury", "Treasury"),
                button -> FactionScreens.openTreasury(snapshot, true, "")
        ).bounds(rightColumn, row0 + rowStep, columnWidth, 20).build());

        Button claims = addRenderableWidget(Button.builder(
                text("screen.kingdoms.claim_map", "Open claim map"),
                button -> FactionScreens.openClaims(snapshot, true, "")
        ).bounds(rightColumn, row0 + rowStep * 2, columnWidth, 20).build());
        claims.active = snapshot.canClaim();

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh", "Refresh"),
                button -> requestRefresh()
        ).bounds(rightColumn, row0 + rowStep * 3, columnWidth, 20).build());
    }

    private Component pvpLabel() {
        String state = snapshot.internalPvp()
                ? text("screen.kingdoms.on", "ON").getString()
                : text("screen.kingdoms.off", "OFF").getString();
        return text("screen.kingdoms.pvp", "Friendly PvP").copy().append(": " + state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left + 16, top + 33, left + 28, top + 45, 0xFF000000 | selectedColor);
        graphics.drawString(font, snapshot.name(), left + 33, top + 35, 0x3F2A19, false);

        graphics.drawString(
                font,
                text("screen.kingdoms.owner", "Owner: ").copy().append(snapshot.ownerName()),
                left + 16,
                top + 51,
                0x4C3824,
                false
        );
        graphics.drawString(
                font,
                Component.literal("PvP: " + (snapshot.internalPvp()
                        ? text("screen.kingdoms.on", "ON").getString()
                        : text("screen.kingdoms.off", "OFF").getString())),
                left + 200,
                top + 51,
                0x4C3824,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.treasury", "Treasury").copy().append(": " + spurs(snapshot.treasury())),
                left + 16,
                top + 63,
                0x4C3824,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.influence", "Influence").copy().append(": " + snapshot.influence()),
                left + 200,
                top + 63,
                0x4C3824,
                false
        );
    }

    static String spurs(long amount) {
        return amount + (amount == 1L ? " spur" : " spurs");
    }
}
