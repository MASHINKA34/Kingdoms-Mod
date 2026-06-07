package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
        nameBox = new EditBox(
                font,
                left + 18,
                top + 57,
                174,
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
        ).bounds(left + 18, top + 85, 174, 20).build());
        color.active = snapshot.canManage();

        Button save = addRenderableWidget(Button.builder(
                text("screen.kingdoms.save", "Save changes"),
                button -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SUpdateFaction(snapshot.tablePos(), nameBox.getValue(), selectedColor)
                )
        ).bounds(left + 18, top + 113, 174, 20).build());
        save.active = snapshot.canManage();

        Button claims = addRenderableWidget(Button.builder(
                text("screen.kingdoms.claim_map", "Open claim map"),
                button -> FactionScreens.openClaims(snapshot, true, "")
        ).bounds(left + 18, top + 141, 174, 20).build());
        claims.active = snapshot.canClaim();

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh", "Refresh"),
                button -> requestRefresh()
        ).bounds(left + 211, top + 141, 101, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left + 201, top + 42, left + 203, top + 170, 0x8066513A);
        graphics.fill(left + 18, top + 39, left + 32, top + 53, 0xFF000000 | selectedColor);
        graphics.drawString(font, snapshot.name(), left + 38, top + 42, 0x3F2A19, false);
        graphics.drawString(
                font,
                text("screen.kingdoms.owner", "Owner: ").copy().append(snapshot.ownerName()),
                left + 211,
                top + 43,
                0x3F2A19,
                false
        );
        graphics.drawString(
                font,
                text("screen.kingdoms.members", "Members"),
                left + 211,
                top + 61,
                0x3F2A19,
                false
        );

        int y = top + 75;
        int shown = Math.min(snapshot.members().size(), 6);
        for (int i = 0; i < shown; i++) {
            FactionSnapshot.Member member = snapshot.members().get(i);
            graphics.drawString(font, member.name(), left + 211, y, 0x4C3824, false);
            graphics.drawString(font, member.role(), left + 269, y, 0x715D43, false);
            y += 11;
        }
        if (snapshot.members().size() > shown) {
            graphics.drawString(font, "+" + (snapshot.members().size() - shown), left + 211, y, 0x715D43, false);
        }
    }
}
