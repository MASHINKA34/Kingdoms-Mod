package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionActionsScreen extends FactionScreen {
    private EditBox playerBox;
    private CycleButton<String> warTarget;
    private boolean confirmingDisband;

    public FactionActionsScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.actions"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        int leftColumn = left + 16;
        int rightColumn = left + 170;
        int columnWidth = 150;

        playerBox = new EditBox(font, leftColumn, top + 56, columnWidth, 20, text("screen.kingdoms.player_name"));
        playerBox.setMaxLength(16);
        playerBox.setHint(text("screen.kingdoms.player_name"));
        addRenderableWidget(playerBox);

        Button invite = addRenderableWidget(Button.builder(
                text("screen.kingdoms.invite"),
                button -> sendPlayerAction(true)
        ).bounds(leftColumn, top + 80, 72, 20).build());
        Button transfer = addRenderableWidget(Button.builder(
                text("screen.kingdoms.transfer"),
                button -> sendPlayerAction(false)
        ).bounds(leftColumn + 78, top + 80, 72, 20).build());

        Button leave = addRenderableWidget(Button.builder(
                text("screen.kingdoms.leave"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2SLeaveFaction(snapshot.tablePos()))
        ).bounds(leftColumn, top + 104, 72, 20).build());
        leave.active = snapshot.hasFaction();

        confirmingDisband = false;
        Button disband = addRenderableWidget(Button.builder(
                text("screen.kingdoms.disband"),
                button -> {
                    if (!confirmingDisband) {
                        confirmingDisband = true;
                        button.setMessage(text("screen.kingdoms.disband_confirm"));
                    } else {
                        PacketDistributor.sendToServer(
                                new FactionPayloads.C2SDisbandFaction(snapshot.tablePos()));
                    }
                }
        ).bounds(leftColumn + 78, top + 104, 72, 20).build());
        disband.active = snapshot.canManage();

        boolean hasTargets = !snapshot.knownFactions().isEmpty();
        Button declareWar;
        if (hasTargets) {
            warTarget = CycleButton.<String>builder(Component::literal)
                    .withValues(snapshot.knownFactions())
                    .withInitialValue(snapshot.knownFactions().get(0))
                    .displayOnlyValue()
                    .create(rightColumn, top + 56, columnWidth, 20, text("screen.kingdoms.target_faction"));
            addRenderableWidget(warTarget);

            declareWar = addRenderableWidget(Button.builder(
                    text("screen.kingdoms.war_declare"),
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SDeclareWar(snapshot.tablePos(), warTarget.getValue()))
            ).bounds(rightColumn, top + 80, columnWidth, 20).build());
            declareWar.active = snapshot.canManage();
        } else {
            warTarget = null;
            Button none = addRenderableWidget(Button.builder(
                    text("screen.kingdoms.no_factions"),
                    button -> {
                    }
            ).bounds(rightColumn, top + 56, columnWidth, 20).build());
            none.active = false;
            declareWar = addRenderableWidget(Button.builder(
                    text("screen.kingdoms.war_declare"),
                    button -> {
                    }
            ).bounds(rightColumn, top + 80, columnWidth, 20).build());
            declareWar.active = false;
        }

        Button endWar = addRenderableWidget(Button.builder(
                text("screen.kingdoms.war_end"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2SEndWar(snapshot.tablePos()))
        ).bounds(rightColumn, top + 104, columnWidth, 20).build());
        endWar.active = snapshot.canManage();

        playerBox.setResponder(value -> {
            boolean named = !value.trim().isEmpty();
            invite.active = named && snapshot.isOfficer();
            transfer.active = named && snapshot.canManage();
        });
        invite.active = false;
        transfer.active = false;

        addRenderableWidget(Button.builder(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openRoot(snapshot, true, "")
        ).bounds(left + 16, top + PANEL_HEIGHT - 25, 70, 20).build());
        addRenderableWidget(Button.builder(
                text("screen.kingdoms.refresh"),
                button -> requestRefresh()
        ).bounds(left + 90, top + PANEL_HEIGHT - 25, 70, 20).build());
    }

    private void sendPlayerAction(boolean invite) {
        String target = playerBox.getValue().trim();
        if (target.isEmpty()) {
            return;
        }
        if (invite) {
            PacketDistributor.sendToServer(new FactionPayloads.C2SInvitePlayer(snapshot.tablePos(), target));
        } else {
            PacketDistributor.sendToServer(new FactionPayloads.C2STransferLeadership(snapshot.tablePos(), target));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, text("screen.kingdoms.members_section"), left + 16, top + 44, 0x3F2A19, false);
        graphics.drawString(font, text("screen.kingdoms.war_section"), left + 170, top + 44, 0x3F2A19, false);
    }
}
