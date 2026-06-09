package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionCreateScreen extends FactionScreen {
    private EditBox nameBox;
    private int selectedColor;

    public FactionCreateScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.faction_create"), snapshot, successful, message);
        selectedColor = snapshot.color();
    }

    @Override
    protected void initFactionWidgets() {
        nameBox = new EditBox(
                font,
                left + 78,
                top + 62,
                180,
                20,
                text("screen.kingdoms.faction_name")
        );
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.color"),
                button -> selectedColor = nextColor(selectedColor)
        ).bounds(left + 78, top + 96, 180, 20).build());

        Button create = addRenderableWidget(Button.builder(
                text("screen.kingdoms.create"),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SCreateFaction(snapshot.tablePos(), nameBox.getValue(), selectedColor)
                )
        ).bounds(left + 78, top + 142, 180, 20).build());
        nameBox.setResponder(value -> create.active = value.trim().length() >= 3);
        create.active = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(
                font,
                text("screen.kingdoms.faction_name"),
                left + 78,
                top + 50,
                0x3F2A19,
                false
        );
        graphics.fill(left + 58, top + 98, left + 72, top + 112, 0xFF3F2A19);
        graphics.fill(left + 60, top + 100, left + 70, top + 110, 0xFF000000 | selectedColor);
        graphics.drawCenteredString(
                font,
                text("screen.kingdoms.create_hint"),
                left + PANEL_WIDTH / 2,
                top + 124,
                0x5B452E
        );
    }
}
