package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.client.EmblemTextures;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionActionsScreen extends FactionScreen {
    private boolean confirmingDisband;
    private boolean confirmingSurrender;

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
                leftColumn, top + 118, 52, 20
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
                leftColumn + 56, top + 118, 70, 20
        ));
        disband.active = snapshot.canManage();

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.influence_open"),
                button -> FactionScreens.openInfluence(snapshot, true, ""),
                leftColumn, top + 142, columnWidth, 20
        ));

        boolean hasActiveWar = !snapshot.warWith().isBlank();
        int nextDiplomacyY = top + 70;
        if (snapshot.hasPendingWarSpoils()) {
            KingdomsButton warSpoils = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.war_spoils"),
                    button -> FactionScreens.openWarSpoils(snapshot.pendingWarSpoils()),
                    rightColumn, nextDiplomacyY, columnWidth, 20
            ));
            warSpoils.active = snapshot.canManage();
            nextDiplomacyY += 24;
        }

        long warCooldown = snapshot.warDeclareCooldownSeconds();
        Component declareLabel = warCooldown > 0L
                ? Component.translatable("screen.kingdoms.war_declare_cooldown",
                        CooldownNoticeScreen.compactRemaining(warCooldown))
                : text("screen.kingdoms.war_declare");
        KingdomsButton declareWar = addRenderableWidget(KingdomsButton.create(
                declareLabel,
                button -> onDeclareWar(),
                rightColumn, nextDiplomacyY, columnWidth, 20
        ));
        declareWar.active = snapshot.canManage() && !hasActiveWar && !snapshot.knownFactions().isEmpty();

        confirmingSurrender = false;
        nextDiplomacyY += 24;
        if (hasActiveWar) {
            KingdomsButton surrenderWar = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.war_surrender"),
                    button -> {
                        if (!confirmingSurrender) {
                            confirmingSurrender = true;
                            button.setMessage(text("screen.kingdoms.war_surrender_confirm"));
                        } else {
                            PacketDistributor.sendToServer(new FactionPayloads.C2SSurrenderWar(snapshot.tablePos()));
                        }
                    },
                    rightColumn, nextDiplomacyY, columnWidth, 20
            ));
            surrenderWar.active = snapshot.canManage();
            nextDiplomacyY += 24;
        }

        if (!snapshot.joinableAllies().isEmpty()) {
            KingdomsButton joinWar = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.war_join"),
                    button -> openJoinWarPicker(),
                    rightColumn, nextDiplomacyY, columnWidth, 20
            ));
            joinWar.active = snapshot.canManage();
            nextDiplomacyY += 24;
        }

        KingdomsButton requestAlliance = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.alliance_request"),
                button -> openAlliancePicker(),
                rightColumn, nextDiplomacyY, columnWidth, 20
        ));
        requestAlliance.active = snapshot.canManage() && !snapshot.allianceCandidates().isEmpty();
        nextDiplomacyY += 24;

        KingdomsButton breakAlliance = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.alliance_members"),
                button -> openBreakAlliancePicker(),
                rightColumn, nextDiplomacyY, columnWidth, 20
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

    private void onDeclareWar() {
        if (snapshot.warDeclareCooldownSeconds() > 0L) {
            minecraft.setScreen(new CooldownNoticeScreen(this, snapshot.warDeclareCooldownSeconds()));
        } else {
            openWarPicker();
        }
    }

    private void openWarPicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.knownFactions().stream()
                .map(FactionActionsScreen::factionEntry)
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_war_target"),
                entries,
                null,
                entry -> minecraft.setScreen(new DeclareWarScreen(this, snapshot.tablePos(), entry.value()))
        ));
    }

    private void openJoinWarPicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.joinableAllies().stream()
                .map(FactionActionsScreen::factionEntry)
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.select_join_war"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SJoinWar(snapshot.tablePos(), entry.value()))
        ));
    }

    private void openAlliancePicker() {
        List<SelectEntryScreen.Entry> entries = snapshot.allianceCandidates().stream()
                .map(FactionActionsScreen::factionEntry)
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
                .map(FactionActionsScreen::factionEntry)
                .toList();
        minecraft.setScreen(new SelectEntryScreen(
                this,
                text("screen.kingdoms.alliance_members_pick"),
                entries,
                null,
                entry -> PacketDistributor.sendToServer(
                        new FactionPayloads.C2SBreakAlliance(snapshot.tablePos(), entry.value()))
        ));
    }

    private static SelectEntryScreen.Entry factionEntry(FactionSnapshot.FactionRef ref) {
        EmblemTextures.Emblem emblem =
                EmblemTextures.resolve(ref.id(), ref.emblem(), ref.emblemUrl(), ref.color());
        return SelectEntryScreen.Entry.emblem(ref.name(), emblem.texture(), emblem.width(), emblem.height());
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
