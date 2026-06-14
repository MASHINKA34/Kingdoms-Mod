package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionActionsScreen extends FactionScreen {
    private boolean confirmingDisband;

    public FactionActionsScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.actions"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        int leftColumn = left + CONTENT_LEFT;
        int rightColumn = left + 172;
        int columnWidth = 126;

        KingdomsButton invite = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.invite"),
                button -> openInvitePicker(),
                leftColumn, top + 70, columnWidth, 20
        ));
        invite.active = snapshot.isOfficer();

        KingdomsButton transfer = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.transfer"),
                button -> openTransferPicker(),
                leftColumn, top + 94, columnWidth, 20
        ));
        transfer.active = snapshot.canManage() && snapshot.members().size() > 1;

        KingdomsButton leave = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.leave"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2SLeaveFaction(snapshot.tablePos())),
                leftColumn, top + 118, 60, 20
        ));
        leave.active = snapshot.hasFaction();

        confirmingDisband = false;
        KingdomsButton disband = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.disband"),
                button -> {
                    if (!confirmingDisband) {
                        confirmingDisband = true;
                        button.setMessage(text("screen.kingdoms.disband_confirm"));
                    } else {
                        PacketDistributor.sendToServer(
                                new FactionPayloads.C2SDisbandFaction(snapshot.tablePos()));
                    }
                },
                leftColumn + 66, top + 118, 60, 20
        ));
        disband.active = snapshot.canManage();

        KingdomsButton declareWar = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.war_declare"),
                button -> openWarPicker(),
                rightColumn, top + 70, columnWidth, 20
        ));
        declareWar.active = snapshot.canManage() && !snapshot.knownFactions().isEmpty();

        KingdomsButton endWar = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.war_end"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2SEndWar(snapshot.tablePos())),
                rightColumn, top + 94, columnWidth, 20
        ));
        endWar.active = snapshot.canManage();

        KingdomsButton requestAlliance = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.alliance_request"),
                button -> openAlliancePicker(),
                rightColumn, top + 118, columnWidth, 20
        ));
        requestAlliance.active = snapshot.canManage() && !snapshot.allianceCandidates().isEmpty();

        KingdomsButton breakAlliance = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.alliance_break"),
                button -> openBreakAlliancePicker(),
                rightColumn, top + 142, columnWidth, 20
        ));
        breakAlliance.active = snapshot.canManage() && !snapshot.allies().isEmpty();
    }

    private void openInvitePicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.onlinePlayers().stream()
                .map(player -> SelectEntryScreen.Entry.player(
                        player.name(),
                        player.inFaction() ? Component.literal(player.factionName()) : null,
                        !player.inFaction()
                ))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_invite"),
                entries,
                text("kingdoms.error.player_already_member"),
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SInvitePlayer(snapshot.tablePos(), entry.value()))
        ));
    }

    private void openTransferPicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.members().stream()
                .filter(member -> !snapshot.isSelf(member.playerId()))
                .map(member -> SelectEntryScreen.Entry.player(member.name(), roleText(member.role()), true))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_transfer"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2STransferLeadership(snapshot.tablePos(), entry.value()))
        ));
    }

    private void openWarPicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.knownFactions().stream()
                .map(name -> SelectEntryScreen.Entry.swatch(name, 0xB3312C))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_war_target"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SDeclareWar(snapshot.tablePos(), entry.value()))
        ));
    }

    private void openAlliancePicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.allianceCandidates().stream()
                .map(name -> SelectEntryScreen.Entry.swatch(name, 0x3F7B33))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_alliance_target"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SRequestAlliance(snapshot.tablePos(), entry.value()))
        ));
    }

    private void openBreakAlliancePicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.allies().stream()
                .map(name -> SelectEntryScreen.Entry.swatch(name, 0x8C2B2B))
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_alliance_break"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SBreakAlliance(snapshot.tablePos(), entry.value()))
        ));
    }

    private static Component roleText(String role) {
        String normalized = role == null ? "" : role.toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("lead")) {
            return text("kingdoms.role.leader");
        }
        if (normalized.startsWith("off")) {
            return text("kingdoms.role.officer");
        }
        return text("kingdoms.role.member");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, text("screen.kingdoms.members_section"), left + CONTENT_LEFT, top + 59, TEXT_DARK, false);
        graphics.drawString(
                font,
                text("screen.kingdoms.diplomacy_section"),
                left + 172,
                top + 59,
                TEXT_DARK,
                false
        );
    }
}
