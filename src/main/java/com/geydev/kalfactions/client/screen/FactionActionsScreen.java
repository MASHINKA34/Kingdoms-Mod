package com.geydev.kalfactions.client.screen;

import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionSnapshot;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionActionsScreen extends FactionScreen {
    private CycleButton<FactionSnapshot.OnlinePlayer> playerTarget;
    private CycleButton<String> warTarget;
    private boolean confirmingDisband;

    public FactionActionsScreen(FactionSnapshot snapshot, boolean successful, String message) {
        super(text("screen.kingdoms.actions"), snapshot, successful, message);
    }

    @Override
    protected void initFactionWidgets() {
        int leftColumn = left + CONTENT_LEFT;
        int rightColumn = left + 172;
        int columnWidth = 126;

        List<FactionSnapshot.OnlinePlayer> players = snapshot.onlinePlayers();
        boolean hasPlayers = !players.isEmpty();
        if (hasPlayers) {
            playerTarget = CycleButton.<FactionSnapshot.OnlinePlayer>builder(FactionActionsScreen::playerLabel)
                    .withValues(players)
                    .withInitialValue(players.get(0))
                    .displayOnlyValue()
                    .create(leftColumn, top + 70, columnWidth, 20, text("screen.kingdoms.player_name"));
            addRenderableWidget(playerTarget);
        } else {
            playerTarget = null;
            KingdomsButton none = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.no_players"),
                    button -> {
                    },
                    leftColumn, top + 70, columnWidth, 20
            ));
            none.active = false;
        }

        KingdomsButton invite = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.invite"),
                button -> sendPlayerAction(true),
                leftColumn, top + 94, 60, 20
        ));
        KingdomsButton transfer = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.transfer"),
                button -> sendPlayerAction(false),
                leftColumn + 66, top + 94, 60, 20
        ));
        invite.active = hasPlayers && snapshot.isOfficer();
        transfer.active = hasPlayers && snapshot.canManage();

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

        boolean hasTargets = !snapshot.knownFactions().isEmpty();
        KingdomsButton declareWar;
        if (hasTargets) {
            warTarget = CycleButton.<String>builder(Component::literal)
                    .withValues(snapshot.knownFactions())
                    .withInitialValue(snapshot.knownFactions().get(0))
                    .displayOnlyValue()
                    .create(rightColumn, top + 70, columnWidth, 20, text("screen.kingdoms.target_faction"));
            addRenderableWidget(warTarget);

            declareWar = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.war_declare"),
                    button -> PacketDistributor.sendToServer(
                            new FactionPayloads.C2SDeclareWar(snapshot.tablePos(), warTarget.getValue())),
                    rightColumn, top + 94, columnWidth, 20
            ));
            declareWar.active = snapshot.canManage();
        } else {
            warTarget = null;
            KingdomsButton none = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.no_factions"),
                    button -> {
                    },
                    rightColumn, top + 70, columnWidth, 20
            ));
            none.active = false;
            declareWar = addRenderableWidget(KingdomsButton.create(
                    text("screen.kingdoms.war_declare"),
                    button -> {
                    },
                    rightColumn, top + 94, columnWidth, 20
            ));
            declareWar.active = false;
        }

        KingdomsButton endWar = addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.war_end"),
                button -> PacketDistributor.sendToServer(new FactionPayloads.C2SEndWar(snapshot.tablePos())),
                rightColumn, top + 118, columnWidth, 20
        ));
        endWar.active = snapshot.canManage();

        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.back"),
                button -> FactionScreens.openRoot(snapshot, true, ""),
                left + 16, top + PANEL_HEIGHT - 25, 70, 20
        ));
        addRenderableWidget(KingdomsButton.create(
                text("screen.kingdoms.refresh"),
                button -> requestRefresh(),
                left + 90, top + PANEL_HEIGHT - 25, 70, 20
        ));
    }

    private void sendPlayerAction(boolean invite) {
        if (playerTarget == null) {
            return;
        }
        FactionSnapshot.OnlinePlayer target = playerTarget.getValue();
        if (target == null || target.name().isEmpty()) {
            return;
        }
        if (invite) {
            if (target.inFaction()) {
                acceptStatus(text("kingdoms.error.player_already_member").getString(), false);
                return;
            }
            PacketDistributor.sendToServer(new FactionPayloads.C2SInvitePlayer(snapshot.tablePos(), target.name()));
        } else {
            if (!target.factionName().equals(snapshot.name())) {
                acceptStatus(text("kingdoms.error.target_not_in_faction").getString(), false);
                return;
            }
            PacketDistributor.sendToServer(new FactionPayloads.C2STransferLeadership(snapshot.tablePos(), target.name()));
        }
    }

    private static Component playerLabel(FactionSnapshot.OnlinePlayer player) {
        if (!player.inFaction()) {
            return Component.literal(player.name());
        }
        return Component.literal(player.name() + " — " + player.factionName()).withStyle(style -> style.withColor(0x9A8F7A));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, text("screen.kingdoms.members_section"), left + CONTENT_LEFT, top + 59, TEXT_DARK, false);
        graphics.drawString(font, text("screen.kingdoms.war_section"), left + 172, top + 59, TEXT_DARK, false);
    }
}
