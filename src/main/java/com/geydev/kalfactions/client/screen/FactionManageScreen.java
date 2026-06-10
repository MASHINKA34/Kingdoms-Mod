package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.integration.xaero.XaeroMaps;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionManageScreen extends FactionScreen {
    private EditBox nameBox;
    private String nameValue;
    private int selectedColor;

    public FactionManageScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.faction_manage"), snapshot, successful, message);
        selectedColor = snapshot.color();
        nameValue = snapshot.name();
    }

    @Override
    public void acceptServerState(FactionSnapshot newSnapshot, boolean actionSuccessful, String message) {
        selectedColor = newSnapshot.color();
        nameValue = newSnapshot.name();
        super.acceptServerState(newSnapshot, actionSuccessful, message);
    }

    @Override
    protected void initFactionWidgets() {
        int leftColumn = left + CONTENT_LEFT;
        int rightColumn = left + 172;
        int columnWidth = 126;
        int row0 = top + 84;
        int rowStep = 22;

        nameBox = new EditBox(
                font,
                leftColumn,
                row0,
                columnWidth,
                20,
                text("screen.kingdoms.faction_name")
        );
        nameBox.setMaxLength(32);
        nameBox.setValue(nameValue);
        nameBox.setResponder(value -> nameValue = value);
        nameBox.setEditable(snapshot.canManage());
        addRenderableWidget(nameBox);

        KingdomsButton color = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.color"),
                button -> minecraft.setScreen(new ColorPickerScreen(this, selectedColor, picked -> selectedColor = picked)),
                leftColumn, row0 + rowStep, columnWidth, 20
        ));
        color.active = snapshot.canManage();

        KingdomsButton save = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.save"),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SUpdateFaction(snapshot.tablePos(), nameBox.getValue(), selectedColor)
                ),
                leftColumn, row0 + rowStep * 2, columnWidth, 20
        ));
        save.active = snapshot.canManage();

        KingdomsButton pvp = addRenderableWidget(KingdomsButton.create(
                pvpLabel(),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SSetPvp(snapshot.tablePos(), !snapshot.internalPvp())
                ),
                leftColumn, row0 + rowStep * 3, columnWidth, 20
        ));
        pvp.active = snapshot.isOfficer();

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.members_count", snapshot.members().size()),
                button -> FactionScreens.openMembers(snapshot, true, ""),
                rightColumn, row0, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.treasury"),
                button -> FactionScreens.openTreasury(snapshot, true, ""),
                rightColumn, row0 + rowStep, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.claim_map.open"),
                button -> {
                    if (!XaeroMaps.openWorldMap()) {
                        FactionScreens.openClaims(snapshot, true, "");
                    }
                },
                rightColumn, row0 + rowStep * 2, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.refresh"),
                button -> requestRefresh(),
                rightColumn, row0 + rowStep * 3, columnWidth, 20
        ));

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.actions_open"),
                button -> FactionScreens.openActions(snapshot, true, ""),
                leftColumn, row0 + rowStep * 4, CONTENT_RIGHT - CONTENT_LEFT, 20
        ));
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
        int leftColumn = left + CONTENT_LEFT;
        int rightColumn = left + 172;

        graphics.fill(leftColumn - 1, top + 57, leftColumn + 11, top + 69, 0xFF1A140C);
        graphics.fill(leftColumn, top + 58, leftColumn + 10, top + 68, 0xFF000000 | selectedColor);
        graphics.drawString(font, snapshot.name(), leftColumn + 15, top + 59, TEXT_DARK, false);
        graphics.drawString(
                font,
                text("screen.kingdoms.owner", snapshot.ownerName()),
                rightColumn,
                top + 59,
                TEXT_MUTED,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.treasury_balance", currency(snapshot.treasury())),
                leftColumn,
                top + 71,
                TEXT_MUTED,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.influence_value", snapshot.influence()),
                rightColumn,
                top + 71,
                TEXT_MUTED,
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
