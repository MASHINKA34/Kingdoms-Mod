package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionCreateScreen extends FactionScreen {
    private EditBox nameBox;
    private String nameValue = "";
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
                top + 74,
                180,
                20,
                text("screen.kingdoms.faction_name")
        );
        nameBox.setMaxLength(32);
        nameBox.setValue(nameValue);
        addRenderableWidget(nameBox);

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.color"),
                button -> minecraft.setScreen(new ColorPickerScreen(this, selectedColor, picked -> selectedColor = picked)),
                left + 78, top + 102, 180, 20
        ));

        KingdomsButton create = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.create"),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SCreateFaction(snapshot.tablePos(), nameBox.getValue(), selectedColor)
                ),
                left + 78, top + 146, 180, 20
        ));
        nameBox.setResponder(value -> {
            nameValue = value;
            create.active = value.trim().length() >= 3;
        });
        create.active = nameValue.trim().length() >= 3;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(
                font,
                text("screen.kingdoms.faction_name"),
                left + 78,
                top + 63,
                TEXT_DARK,
                false
        );
        graphics.fill(left + 56, top + 104, left + 74, top + 122, 0xFF1A140C);
        graphics.fill(left + 58, top + 106, left + 72, top + 120, 0xFF000000 | selectedColor);
        int hintWidth = font.width(text("screen.kingdoms.create_hint"));
        graphics.drawString(
                font,
                text("screen.kingdoms.create_hint"),
                left + (PANEL_WIDTH - hintWidth) / 2,
                top + 130,
                TEXT_HINT,
                false
        );
    }
}
