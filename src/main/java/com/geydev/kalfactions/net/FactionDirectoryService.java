package com.geydev.kalfactions.net;

import static com.geydev.kalfactions.net.FactionServerHooks.sendNotice;
import static com.geydev.kalfactions.net.FactionServerHooks.sendNoticeToFaction;

import com.geydev.kalfactions.command.PendingAllianceRequests;
import com.geydev.kalfactions.command.PendingFactionInvites;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.war.War;
import com.geydev.kalfactions.war.WarManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionDirectoryService {
    private static final Map<UUID, Long> LAST_LIST_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTION_TICK = new HashMap<>();

    public static void requestFactionList(ServerPlayer player) {
        if (player.isAlive()
                && ActionCooldown.mark(LAST_LIST_TICK, player.getUUID(), player.server.getTickCount(), 20L) == null) {
            sendFactionList(player);
        }
    }

    private static boolean allowAction(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator()
                && ActionCooldown.mark(LAST_ACTION_TICK, player.getUUID(), player.server.getTickCount(), 4L) == null;
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_LIST_TICK.remove(playerId);
        LAST_ACTION_TICK.remove(playerId);
    }

    public static void pushInviteBadge(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CInviteBadge(countPendingInvites(player)));
    }

    public static int countPendingInvites(ServerPlayer player) {
        int count = PendingFactionInvites.allFor(player.getServer(), player.getUUID()).size();
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction ownFaction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (ownFaction != null && ownFaction.ownerId().equals(player.getUUID())) {
            count += PendingAllianceRequests.allFor(player.getServer(), ownFaction.id()).size();
        }
        return count;
    }

    public static void sendFactionList(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        WarManager wars = WarManager.get(player.getServer());
        List<FactionPayloads.FactionInfo> factions = new ArrayList<>();
        Map<UUID, String> names = new HashMap<>();
        List<Faction> visible = manager.factions().stream()
                .sorted(Comparator.comparing(Faction::name, String.CASE_INSENSITIVE_ORDER))
                .limit(FactionPayloads.S2CFactionList.MAX_FACTIONS)
                .toList();
        for (Faction faction : visible) {
            String warWith = wars.warForFaction(faction.id())
                    .filter(War::isActive)
                    .map(war -> manager.getFactionById(war.opponentOf(faction.id()))
                            .map(Faction::name)
                            .orElse(""))
                    .orElse("");
            List<FactionPayloads.MemberInfo> members = faction.members().values().stream()
                    .sorted(Comparator
                            .comparing((FactionMember member) -> member.role().ordinal()).reversed()
                            .thenComparing(
                                    member -> names.computeIfAbsent(member.playerId(), id -> resolvePlayerName(player, id)),
                                    String.CASE_INSENSITIVE_ORDER))
                    .limit(FactionPayloads.FactionInfo.MAX_LIST_MEMBERS)
                    .map(member -> new FactionPayloads.MemberInfo(
                            names.computeIfAbsent(member.playerId(), id -> resolvePlayerName(player, id)),
                            "kingdoms.role." + member.role().name().toLowerCase(Locale.ROOT)))
                    .toList();
            factions.add(new FactionPayloads.FactionInfo(
                    faction.id(),
                    faction.name(),
                    faction.color(),
                    faction.memberCount(),
                    faction.influence(),
                    warWith,
                    faction.allies().stream()
                            .map(manager::getFactionById)
                            .flatMap(Optional::stream)
                            .map(Faction::name)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .limit(FactionPayloads.FactionInfo.MAX_ALLIES)
                            .toList(),
                    FactionSnapshotFactory.bonusNames(faction),
                    FactionSnapshotFactory.emblemPixels(faction),
                    faction.emblemUrl(),
                    members
            ));
        }
        List<FactionPayloads.InviteInfo> invites = new ArrayList<>();
        for (PendingFactionInvites.Invite invite : PendingFactionInvites.allFor(player.getServer(), player.getUUID())) {
            if (invites.size() >= FactionPayloads.S2CFactionList.MAX_INVITES) {
                break;
            }
            Faction faction = manager.getFactionById(invite.factionId()).orElse(null);
            if (faction == null) {
                continue;
            }
            invites.add(new FactionPayloads.InviteInfo(
                    faction.id(),
                    faction.name(),
                    faction.color(),
                    faction.memberCount(),
                    resolvePlayerName(player, invite.inviterId()),
                    FactionSnapshotFactory.bonusNames(faction),
                    FactionSnapshotFactory.emblemPixels(faction),
                    faction.emblemUrl()
            ));
        }
        List<FactionPayloads.AllianceInviteInfo> allianceInvites = new ArrayList<>();
        Faction ownFaction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (ownFaction != null && ownFaction.ownerId().equals(player.getUUID())) {
            for (PendingAllianceRequests.Request request
                    : PendingAllianceRequests.allFor(player.getServer(), ownFaction.id())) {
                if (allianceInvites.size() >= FactionPayloads.S2CFactionList.MAX_ALLIANCE_INVITES) {
                    break;
                }
                Faction faction = manager.getFactionById(request.fromFactionId()).orElse(null);
                if (faction == null) {
                    continue;
                }
                allianceInvites.add(new FactionPayloads.AllianceInviteInfo(
                        faction.id(),
                        faction.name(),
                        faction.color(),
                        faction.memberCount(),
                        resolvePlayerName(player, request.requesterId()),
                        FactionSnapshotFactory.emblemPixels(faction),
                        faction.emblemUrl()
                ));
            }
        }
        PacketDistributor.sendToPlayer(
                player,
                new FactionPayloads.S2CFactionList(factions, invites, allianceInvites)
        );
        pushInviteBadge(player);
    }

    public static void respondInvite(ServerPlayer player, UUID factionId, boolean accept) {
        if (!allowAction(player)) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        PendingFactionInvites.Invite invite = PendingFactionInvites
                .find(player.getServer(), factionId, player.getUUID())
                .orElse(null);
        if (invite == null) {
            sendNotice(player, Component.translatable("kingdoms.command.faction.invite.not_found"), false);
            requestFactionList(player);
            return;
        }
        if (!accept) {
            PendingFactionInvites.remove(player.getServer(), factionId, player.getUUID());
            sendNotice(player, Component.translatable("kingdoms.command.faction.invite.declined"), true);
            ServerPlayer inviter = player.getServer().getPlayerList().getPlayer(invite.inviterId());
            if (inviter != null) {
                sendNotice(
                        inviter,
                        Component.translatable(
                                "kingdoms.command.faction.invite.declined_notice",
                                player.getGameProfile().getName()
                        ),
                        false
                );
            }
            sendFactionList(player);
            return;
        }
        if (manager.getFactionForMember(player.getUUID()).isPresent()) {
            sendNotice(player, Component.translatable("kingdoms.command.faction.join.leave_current_first"), false);
            requestFactionList(player);
            return;
        }
        FactionManager.OperationResult result = manager.addMember(factionId, player.getUUID());
        if (!result.successful()) {
            Component error = result.status() == FactionManager.Status.FACTION_FULL
                    ? Component.translatable("kingdoms.error.faction_full")
                    : Component.translatable("kingdoms.error.faction_action_rejected");
            sendNotice(player, error, false);
            requestFactionList(player);
            return;
        }
        PendingFactionInvites.remove(player.getServer(), factionId, player.getUUID());
        Faction faction = manager.getFactionById(factionId).orElse(null);
        String factionName = faction == null ? "" : faction.name();
        sendNotice(player, Component.translatable("kingdoms.command.faction.join.success", factionName), true);
        ServerPlayer inviter = player.getServer().getPlayerList().getPlayer(invite.inviterId());
        if (inviter != null) {
            sendNotice(
                    inviter,
                    Component.translatable(
                            "kingdoms.command.faction.join.notice",
                            player.getGameProfile().getName(),
                            factionName
                    ),
                    true
            );
        }
        ClaimSyncManager.resync(player);
        sendFactionList(player);
    }

    public static void respondAlliance(ServerPlayer player, UUID factionId, boolean accept) {
        if (!allowAction(player)) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction target = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (target == null || !target.ownerId().equals(player.getUUID())) {
            sendNotice(player, Component.translatable("kingdoms.error.leader_settings_only"), false);
            requestFactionList(player);
            return;
        }
        PendingAllianceRequests.Request request = PendingAllianceRequests
                .find(player.getServer(), factionId, target.id())
                .orElse(null);
        Faction source = manager.getFactionById(factionId).orElse(null);
        if (request == null || source == null) {
            sendNotice(player, Component.translatable("kingdoms.alliance.request.not_found"), false);
            requestFactionList(player);
            return;
        }
        if (!accept) {
            PendingAllianceRequests.remove(player.getServer(), source.id(), target.id());
            Component notice = Component.translatable(
                    "kingdoms.alliance.request.declined",
                    target.name(),
                    source.name()
            );
            sendNoticeToFaction(player, source, notice, true);
            sendNoticeToFaction(player, target, notice, true);
            sendFactionList(player);
            return;
        }
        if (WarManager.get(player.getServer()).areAtWar(source.id(), target.id())) {
            sendNotice(player, Component.translatable("kingdoms.error.alliance_at_war"), false);
            requestFactionList(player);
            return;
        }
        FactionManager.OperationResult result = manager.addAlliance(source.id(), target.id());
        if (!result.successful()) {
            sendNotice(player, Component.translatable("kingdoms.error.alliance_rejected"), false);
            requestFactionList(player);
            return;
        }
        PendingAllianceRequests.removeBetween(player.getServer(), source.id(), target.id());
        Component notice = Component.translatable("kingdoms.alliance.created", source.name(), target.name());
        sendNoticeToFaction(player, source, notice, true);
        sendNoticeToFaction(player, target, notice, true);
        sendFactionList(player);
    }

    private static String resolvePlayerName(ServerPlayer viewer, UUID playerId) {
        ServerPlayer online = viewer.getServer().getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return viewer.getServer().getProfileCache()
                .get(playerId)
                .map(profile -> profile.getName())
                .orElse(playerId.toString().substring(0, 8));
    }

    private FactionDirectoryService() {
    }
}
