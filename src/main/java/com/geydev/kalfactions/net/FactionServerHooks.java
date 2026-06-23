package com.geydev.kalfactions.net;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.block.FactionTableBlockEntity;
import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.chest.ChestAccessMode;
import com.geydev.kalfactions.command.PendingAllianceRequests;
import com.geydev.kalfactions.command.PendingFactionInvites;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.integration.IntegrationManager;
import com.geydev.kalfactions.war.War;
import com.geydev.kalfactions.war.WarManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FactionServerHooks {
    public static final int MAX_NAME_LENGTH = 32;
    private static final double MAX_TABLE_DISTANCE_SQR = 64.0D;
    private static final long ACTION_COOLDOWN_TICKS = 2L;
    private static final ConcurrentHashMap<UUID, Long> LAST_ACTION_TICK = new ConcurrentHashMap<>();
    private static volatile Service service = new FactionManagerService();

    public static void install(Service newService) {
        service = Objects.requireNonNull(newService, "newService");
    }

    public static Service service() {
        return service;
    }

    public static void openFor(ServerPlayer player, BlockPos tablePos) {
        openFor(player, tablePos, false);
    }

    public static void openFor(ServerPlayer player, BlockPos tablePos, boolean silent) {
        Validation validation = validateTable(player, tablePos, false);
        if (!validation.allowed) {
            if (!silent) {
                send(player, fallbackSnapshot(tablePos), false, false, validation.message);
            }
            return;
        }

        try {
            FactionSnapshot snapshot = sanitizeSnapshot(tablePos, service.view(player, tablePos));
            send(player, snapshot, true, true, Component.empty());
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Failed to open faction table at {} for {}", tablePos, player.getGameProfile().getName(), exception);
            send(
                    player,
                    fallbackSnapshot(tablePos),
                    false,
                    false,
                    Component.translatable("kingdoms.error.faction_data_unavailable")
            );
        }
    }

    public static void create(
            ServerPlayer player,
            BlockPos tablePos,
            String requestedName,
            int requestedColor,
            List<String> bonuses,
            List<Integer> emblem,
            String emblemUrl
    ) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }

        String name = normalizeName(requestedName);
        if (name.length() < 3) {
            reject(player, tablePos, Component.translatable("kingdoms.error.name_too_short"));
            return;
        }

        FactionSnapshot before = safeView(player, tablePos);
        if (before.hasFaction()) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.already_in_faction")
            );
            return;
        }

        perform(player, tablePos, () -> service.create(
                player,
                tablePos,
                name,
                requestedColor & 0xFFFFFF,
                bonuses,
                emblem,
                emblemUrl
        ));
    }

    public static void setEmblem(
            ServerPlayer player,
            BlockPos tablePos,
            List<Integer> emblem,
            String emblemUrl
    ) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.setEmblem(player, tablePos, emblem, emblemUrl));
    }

    public static void update(ServerPlayer player, BlockPos tablePos, String requestedName, int requestedColor) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }

        String name = normalizeName(requestedName);
        if (name.length() < 3) {
            reject(player, tablePos, Component.translatable("kingdoms.error.name_too_short"));
            return;
        }

        FactionSnapshot before = safeView(player, tablePos);
        if (!before.canManage()) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.no_manage_permission")
            );
            return;
        }

        perform(player, tablePos, () -> service.update(player, tablePos, name, requestedColor & 0xFFFFFF));
    }

    public static void setClaim(
            ServerPlayer player,
            BlockPos tablePos,
            int chunkX,
            int chunkZ,
            boolean claimed
    ) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }

        FactionSnapshot before = safeView(player, tablePos);
        if (!before.canClaim()) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.no_claim_permission")
            );
            return;
        }

        int maxDelta = before.mapRadius();
        if (Math.abs(chunkX - before.centerChunkX()) > maxDelta
                || Math.abs(chunkZ - before.centerChunkZ()) > maxDelta) {
            send(
                    player,
                    before,
                    false,
                    true,
                    Component.translatable("kingdoms.error.claim_outside_map")
            );
            return;
        }

        perform(player, tablePos, () -> service.setClaim(player, tablePos, new ChunkPos(chunkX, chunkZ), claimed));
    }

    public static void deposit(ServerPlayer player, BlockPos tablePos, long amount) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.deposit(player, tablePos, amount));
    }

    public static void withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.withdraw(player, tablePos, amount));
    }

    public static void turnInCrystals(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.turnInCrystals(player, tablePos));
    }

    public static void startResearch(ServerPlayer player, BlockPos tablePos, String nodeName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.startResearch(player, tablePos, nodeName));
    }

    public static void kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.kickMember(player, tablePos, targetId));
    }

    public static void setMemberRole(ServerPlayer player, BlockPos tablePos, UUID targetId, String role) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.setMemberRole(player, tablePos, targetId, role));
    }

    public static void setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.setPvp(player, tablePos, enabled));
    }

    public static void leave(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.leave(player, tablePos));
    }

    public static void disband(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.disband(player, tablePos));
    }

    public static void invite(ServerPlayer player, BlockPos tablePos, String targetName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.invite(player, tablePos, targetName));
    }

    public static void transfer(ServerPlayer player, BlockPos tablePos, String targetName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.transfer(player, tablePos, targetName));
    }

    public static void requestAlliance(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.requestAlliance(player, tablePos, targetFactionName));
    }

    public static void breakAlliance(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.breakAlliance(player, tablePos, targetFactionName));
    }

    public static void declareWar(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.declareWar(player, tablePos, targetFactionName));
    }

    public static void joinWar(ServerPlayer player, BlockPos tablePos, String allyFactionName) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.joinWar(player, tablePos, allyFactionName));
    }

    public static void endWar(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.endWar(player, tablePos));
    }

    public static void surrenderWar(ServerPlayer player, BlockPos tablePos) {
        Validation validation = validateTable(player, tablePos, true);
        if (!validation.allowed) {
            reject(player, tablePos, validation.message);
            return;
        }
        perform(player, tablePos, () -> service.surrenderWar(player, tablePos));
    }

    public static void claimWarSpoils(ServerPlayer player, UUID spoilsId, String choiceName) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            sendNotice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }

        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        if (!faction.ownerId().equals(player.getUUID())) {
            sendNotice(player, Component.translatable("kingdoms.error.leader_settings_only"), false);
            return;
        }
        WarManager.SpoilsChoice choice = WarManager.SpoilsChoice.parse(choiceName).orElse(null);
        if (choice == null) {
            sendNotice(player, Component.translatable("kingdoms.error.war_spoils_choice"), false);
            return;
        }

        WarManager.ClaimSpoilsResult result = WarManager.get(player.getServer())
                .claimSpoils(player.getServer(), player, faction.id(), spoilsId, choice);
        Component message = switch (result) {
            case SUCCESS -> Component.translatable("kingdoms.war.spoils_claimed");
            case NOT_WINNER -> Component.translatable("kingdoms.error.war_spoils_not_winner");
            case TRANSFER_FAILED -> Component.translatable("kingdoms.error.war_spoils_transfer_failed");
            case NOT_FOUND -> Component.translatable("kingdoms.error.war_spoils_unavailable");
        };
        sendNotice(player, message, result == WarManager.ClaimSpoilsResult.SUCCESS);
    }

    public static void mapSetClaims(ServerPlayer player, boolean claimed, List<Long> packedChunks) {
        if (!player.isAlive() || player.isSpectator() || packedChunks.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            sendNotice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }
        try {
            Result result = service.mapSetClaims(player, claimed, packedChunks);
            if (!result.message().getString().isBlank()) {
                sendNotice(player, result.message(), result.successful());
            }
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Map claim operation failed for {}", player.getGameProfile().getName(), exception);
            sendNotice(player, Component.translatable("kingdoms.error.faction_action_failed"), false);
        }
    }

    public static void toggleForceLoad(ServerPlayer player, ResourceLocation dimensionId, long packedChunk) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
        if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
            sendNotice(player, Component.translatable("kingdoms.error.action_rate_limited"), false);
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            sendNotice(player, Component.translatable("kingdoms.error.not_in_faction"), false);
            return;
        }
        FactionMember member = faction.member(player.getUUID()).orElse(null);
        if (member == null || !member.role().canManageClaims()) {
            sendNotice(player, Component.translatable("kingdoms.error.role_cannot_change_claims"), false);
            return;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ChunkPos chunk = new ChunkPos(packedChunk);
        FactionManager.ForceLoadResult result = manager.toggleForceLoad(
                player.getServer(),
                faction.id(),
                new com.geydev.kalfactions.claim.ClaimKey(dimension, chunk)
        );
        switch (result) {
            case ENABLED -> sendNotice(player, Component.translatable("kingdoms.command.faction.forceload.enabled"), true);
            case DISABLED -> sendNotice(player, Component.translatable("kingdoms.command.faction.forceload.disabled"), true);
            case LIMIT_REACHED -> sendNotice(
                    player,
                    Component.translatable("kingdoms.command.faction.forceload.limit", manager.forceLoadLimit(faction.id())),
                    false
            );
            case NOT_OWN_CLAIM -> sendNotice(player, Component.translatable("kingdoms.command.faction.forceload.not_own"), false);
            default -> sendNotice(player, Component.translatable("kingdoms.error.faction_action_failed"), false);
        }
        IntegrationManager.refreshFromServer(player.getServer());
        ClaimSyncManager.resync(player);
    }

    public static void sendNotice(ServerPlayer player, Component message, boolean successful) {
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CFactionNotice(message, successful));
    }

    public static void sendFactionList(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        WarManager wars = WarManager.get(player.getServer());
        List<FactionPayloads.FactionInfo> factions = new ArrayList<>();
        for (Faction faction : manager.factions()) {
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
                                    member -> resolvePlayerName(player, member.playerId()),
                                    String.CASE_INSENSITIVE_ORDER))
                    .limit(FactionPayloads.FactionInfo.MAX_LIST_MEMBERS)
                    .map(member -> new FactionPayloads.MemberInfo(
                            resolvePlayerName(player, member.playerId()),
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
                            .toList(),
                    FactionManagerService.bonusNames(faction),
                    FactionManagerService.emblemPixels(faction),
                    faction.emblemUrl(),
                    members
            ));
        }
        factions.sort(Comparator.comparing(FactionPayloads.FactionInfo::name, String.CASE_INSENSITIVE_ORDER));

        List<FactionPayloads.InviteInfo> invites = new ArrayList<>();
        for (PendingFactionInvites.Invite invite : PendingFactionInvites.allFor(player.getServer(), player.getUUID())) {
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
                    FactionManagerService.bonusNames(faction),
                    FactionManagerService.emblemPixels(faction),
                    faction.emblemUrl()
            ));
        }
        List<FactionPayloads.AllianceInviteInfo> allianceInvites = new ArrayList<>();
        Faction ownFaction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (ownFaction != null && ownFaction.ownerId().equals(player.getUUID())) {
            for (PendingAllianceRequests.Request request
                    : PendingAllianceRequests.allFor(player.getServer(), ownFaction.id())) {
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
                        FactionManagerService.emblemPixels(faction),
                        faction.emblemUrl()
                ));
            }
        }
        PacketDistributor.sendToPlayer(
                player,
                new FactionPayloads.S2CFactionList(factions, invites, allianceInvites)
        );
    }

    public static void respondInvite(ServerPlayer player, UUID factionId, boolean accept) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        PendingFactionInvites.Invite invite = PendingFactionInvites
                .find(player.getServer(), factionId, player.getUUID())
                .orElse(null);
        if (invite == null) {
            sendNotice(player, Component.translatable("kingdoms.command.faction.invite.not_found"), false);
            sendFactionList(player);
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
            sendFactionList(player);
            return;
        }
        FactionManager.OperationResult result = manager.addMember(factionId, player.getUUID());
        if (!result.successful()) {
            sendNotice(player, Component.translatable("kingdoms.error.faction_action_rejected"), false);
            sendFactionList(player);
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
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction target = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (target == null || !target.ownerId().equals(player.getUUID())) {
            sendNotice(player, Component.translatable("kingdoms.error.leader_settings_only"), false);
            sendFactionList(player);
            return;
        }
        PendingAllianceRequests.Request request = PendingAllianceRequests
                .find(player.getServer(), factionId, target.id())
                .orElse(null);
        Faction source = manager.getFactionById(factionId).orElse(null);
        if (request == null || source == null) {
            sendNotice(player, Component.translatable("kingdoms.alliance.request.not_found"), false);
            sendFactionList(player);
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
            sendFactionList(player);
            return;
        }
        FactionManager.OperationResult result = manager.addAlliance(source.id(), target.id());
        if (!result.successful()) {
            sendNotice(player, Component.translatable("kingdoms.error.alliance_rejected"), false);
            sendFactionList(player);
            return;
        }
        PendingAllianceRequests.removeBetween(player.getServer(), source.id(), target.id());
        Component notice = Component.translatable("kingdoms.alliance.created", source.name(), target.name());
        sendNoticeToFaction(player, source, notice, true);
        sendNoticeToFaction(player, target, notice, true);
        sendFactionList(player);
    }

    static void sendNoticeToFaction(ServerPlayer context, Faction faction, Component message, boolean successful) {
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer member = context.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                sendNotice(member, message, successful);
            }
        }
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

    public static void setChestMode(ServerPlayer player, BlockPos pos, String modeName) {
        if (!validateChestRequest(player, pos)) {
            return;
        }
        ChestAccessMode mode;
        try {
            mode = ChestAccessMode.valueOf(modeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sendNotice(player, Component.translatable("kingdoms.error.faction_action_rejected"), false);
            return;
        }
        ServerLevel level = player.serverLevel();
        AccessTool.WhitelistResult result = AccessTool.setMode(player, level, pos, mode);
        sendChestAccessState(player, level, pos, result.message(), result.success());
    }

    public static void editChestWhitelist(
            ServerPlayer player,
            BlockPos pos,
            boolean add,
            UUID targetId,
            String targetName
    ) {
        if (!validateChestRequest(player, pos)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        AccessTool.WhitelistResult result;
        if (add) {
            ServerPlayer target = player.getServer().getPlayerList().getPlayerByName(targetName.trim());
            if (target == null) {
                sendChestAccessState(
                        player,
                        level,
                        pos,
                        Component.translatable("kingdoms.duel.error.player_offline"),
                        false
                );
                return;
            }
            result = AccessTool.addWhitelistPlayer(
                    player,
                    level,
                    pos,
                    target.getUUID(),
                    target.getGameProfile().getName()
            );
        } else {
            result = AccessTool.removeWhitelistPlayer(player, level, pos, targetId, targetName);
        }
        sendChestAccessState(player, level, pos, result.message(), result.success());
    }

    public static void sendChestAccessState(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            Component notice,
            boolean successful
    ) {
        AccessTool.AccessState state = AccessTool.stateFor(player, level, pos);
        if (state == null) {
            if (!notice.getString().isBlank()) {
                sendNotice(player, notice, successful);
            }
            return;
        }
        FactionManager manager = FactionManager.get(level);
        Set<UUID> listed = new HashSet<>();
        List<FactionPayloads.ChestWhitelistEntry> whitelist = new ArrayList<>();
        for (AccessTool.StateEntry entry : state.whitelist()) {
            listed.add(entry.id());
            whitelist.add(new FactionPayloads.ChestWhitelistEntry(entry.id(), entry.name()));
        }
        List<String> candidates = player.getServer().getPlayerList().getPlayers().stream()
                .filter(online -> !online.getUUID().equals(player.getUUID()))
                .filter(online -> !listed.contains(online.getUUID()))
                .filter(online -> !state.factionId().equals(
                        manager.getFactionIdForMember(online.getUUID()).orElse(null)))
                .map(online -> online.getGameProfile().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .limit(FactionPayloads.S2CChestAccessState.MAX_CANDIDATES)
                .toList();
        PacketDistributor.sendToPlayer(player, new FactionPayloads.S2CChestAccessState(
                pos,
                state.mode().name(),
                whitelist,
                candidates,
                notice,
                successful
        ));
    }

    private static boolean validateChestRequest(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || !player.level().isLoaded(pos)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                <= MAX_TABLE_DISTANCE_SQR;
    }

    public static void clearRateLimit(UUID playerId) {
        LAST_ACTION_TICK.remove(playerId);
    }

    private static void perform(ServerPlayer player, BlockPos tablePos, Operation operation) {
        try {
            Result result = Objects.requireNonNull(operation.run(), "Faction service returned null");
            FactionSnapshot snapshot = sanitizeSnapshot(
                    tablePos,
                    result.snapshot == null ? service.view(player, tablePos) : result.snapshot
            );
            send(player, snapshot, result.successful, true, result.message);
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Faction operation failed at {} for {}", tablePos, player.getGameProfile().getName(), exception);
            reject(
                    player,
                    tablePos,
                    Component.translatable("kingdoms.error.faction_action_failed")
            );
        }
    }

    private static FactionSnapshot safeView(ServerPlayer player, BlockPos tablePos) {
        try {
            return sanitizeSnapshot(tablePos, service.view(player, tablePos));
        } catch (RuntimeException exception) {
            KalFactions.LOGGER.error("Failed to read faction data at {}", tablePos, exception);
            return fallbackSnapshot(tablePos);
        }
    }

    private static Validation validateTable(ServerPlayer player, BlockPos tablePos, boolean rateLimited) {
        if (!player.isAlive() || player.isSpectator()) {
            return Validation.deny(Component.translatable("kingdoms.error.table_unavailable_now"));
        }
        if (!player.level().isLoaded(tablePos)) {
            return Validation.deny(Component.translatable("kingdoms.error.table_not_loaded"));
        }
        if (player.distanceToSqr(tablePos.getX() + 0.5D, tablePos.getY() + 0.5D, tablePos.getZ() + 0.5D)
                > MAX_TABLE_DISTANCE_SQR) {
            return Validation.deny(Component.translatable("kingdoms.error.table_too_far"));
        }
        if (!(player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity)) {
            return Validation.deny(Component.translatable("kingdoms.error.not_faction_table"));
        }
        if (rateLimited) {
            long now = player.level().getGameTime();
            Long previous = LAST_ACTION_TICK.put(player.getUUID(), now);
            if (previous != null && now - previous < ACTION_COOLDOWN_TICKS) {
                return Validation.deny(Component.translatable("kingdoms.error.action_rate_limited"));
            }
        }
        return Validation.ALLOW;
    }

    private static String normalizeName(String value) {
        StringBuilder normalized = new StringBuilder(MAX_NAME_LENGTH);
        if (value != null) {
            value.codePoints()
                    .filter(codePoint -> !Character.isISOControl(codePoint))
                    .limit(MAX_NAME_LENGTH)
                    .forEach(normalized::appendCodePoint);
        }
        return normalized.toString().trim().replaceAll("\\s+", " ");
    }

    private static FactionSnapshot sanitizeSnapshot(BlockPos tablePos, FactionSnapshot snapshot) {
        if (snapshot == null) {
            return fallbackSnapshot(tablePos);
        }
        return new FactionSnapshot(
                tablePos,
                snapshot.factionId(),
                snapshot.name(),
                snapshot.ownerName(),
                snapshot.color(),
                snapshot.canManage(),
                snapshot.canClaim(),
                snapshot.centerChunkX(),
                snapshot.centerChunkZ(),
                snapshot.mapRadius(),
                snapshot.members(),
                snapshot.claims(),
                snapshot.treasury(),
                snapshot.influence(),
                snapshot.influenceScience(),
                snapshot.influenceEconomic(),
                snapshot.influenceMilitary(),
                snapshot.internalPvp(),
                snapshot.creationCost(),
                snapshot.viewerId(),
                snapshot.isOfficer(),
                snapshot.warWith(),
                snapshot.knownFactions(),
                snapshot.allianceCandidates(),
                snapshot.allies(),
                snapshot.joinableAllies(),
                snapshot.onlinePlayers(),
                snapshot.bonuses(),
                snapshot.emblem(),
                snapshot.emblemUrl(),
                snapshot.completedResearch(),
                snapshot.activeResearchNode(),
                snapshot.activeResearchEndMillis(),
                snapshot.pendingWarSpoils(),
                snapshot.claimCount(),
                snapshot.forceLoadUsed()
        );
    }

    private static FactionSnapshot fallbackSnapshot(BlockPos tablePos) {
        ChunkPos center = new ChunkPos(tablePos);
        return FactionSnapshot.empty(tablePos, center.x, center.z, 0L);
    }

    private static void reject(ServerPlayer player, BlockPos tablePos, Component message) {
        send(player, safeView(player, tablePos), false, true, message);
    }

    private static void send(
            ServerPlayer player,
            FactionSnapshot snapshot,
            boolean successful,
            boolean openScreen,
            Component message
    ) {
        PacketDistributor.sendToPlayer(
                player,
                new FactionPayloads.S2CFactionState(snapshot, successful, openScreen, message)
        );
    }

    public interface Service {
        FactionSnapshot view(ServerPlayer player, BlockPos tablePos);

        Result create(
                ServerPlayer player,
                BlockPos tablePos,
                String name,
                int color,
                List<String> bonuses,
                List<Integer> emblem,
                String emblemUrl
        );

        Result setEmblem(ServerPlayer player, BlockPos tablePos, List<Integer> emblem, String emblemUrl);

        Result update(ServerPlayer player, BlockPos tablePos, String name, int color);

        Result setClaim(ServerPlayer player, BlockPos tablePos, ChunkPos chunkPos, boolean claimed);

        Result deposit(ServerPlayer player, BlockPos tablePos, long amount);

        Result withdraw(ServerPlayer player, BlockPos tablePos, long amount);

        Result turnInCrystals(ServerPlayer player, BlockPos tablePos);

        Result startResearch(ServerPlayer player, BlockPos tablePos, String nodeName);

        Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId);

        Result setMemberRole(ServerPlayer player, BlockPos tablePos, UUID targetId, String role);

        Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled);

        Result leave(ServerPlayer player, BlockPos tablePos);

        Result disband(ServerPlayer player, BlockPos tablePos);

        Result invite(ServerPlayer player, BlockPos tablePos, String targetName);

        Result transfer(ServerPlayer player, BlockPos tablePos, String targetName);

        Result requestAlliance(ServerPlayer player, BlockPos tablePos, String targetFactionName);

        Result breakAlliance(ServerPlayer player, BlockPos tablePos, String targetFactionName);

        Result declareWar(ServerPlayer player, BlockPos tablePos, String targetFactionName);

        Result joinWar(ServerPlayer player, BlockPos tablePos, String allyFactionName);

        Result endWar(ServerPlayer player, BlockPos tablePos);

        Result surrenderWar(ServerPlayer player, BlockPos tablePos);

        Result mapSetClaims(ServerPlayer player, boolean claimed, List<Long> packedChunks);
    }

    public record Result(boolean successful, Component message, FactionSnapshot snapshot) {
        public static Result success(FactionSnapshot snapshot) {
            return new Result(true, Component.empty(), snapshot);
        }

        public static Result denied(Component message, FactionSnapshot snapshot) {
            return new Result(false, message, snapshot);
        }
    }

    private record Validation(boolean allowed, Component message) {
        private static final Validation ALLOW = new Validation(true, Component.empty());

        private static Validation deny(Component message) {
            return new Validation(false, message);
        }
    }

    @FunctionalInterface
    private interface Operation {
        Result run();
    }

    private static final class UnavailableService implements Service {
        @Override
        public FactionSnapshot view(ServerPlayer player, BlockPos tablePos) {
            return fallbackSnapshot(tablePos);
        }

        @Override
        public Result create(
                ServerPlayer player,
                BlockPos tablePos,
                String name,
                int color,
                List<String> bonuses,
                List<Integer> emblem,
                String emblemUrl
        ) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setEmblem(ServerPlayer player, BlockPos tablePos, List<Integer> emblem, String emblemUrl) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result update(ServerPlayer player, BlockPos tablePos, String name, int color) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setClaim(ServerPlayer player, BlockPos tablePos, ChunkPos chunkPos, boolean claimed) {
            return Result.denied(
                    Component.translatable("kingdoms.error.claims_unavailable"),
                    view(player, tablePos)
            );
        }

        @Override
        public Result deposit(ServerPlayer player, BlockPos tablePos, long amount) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result turnInCrystals(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result startResearch(ServerPlayer player, BlockPos tablePos, String nodeName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setMemberRole(ServerPlayer player, BlockPos tablePos, UUID targetId, String role) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result leave(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result disband(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result invite(ServerPlayer player, BlockPos tablePos, String targetName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result transfer(ServerPlayer player, BlockPos tablePos, String targetName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result requestAlliance(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result breakAlliance(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result declareWar(ServerPlayer player, BlockPos tablePos, String targetFactionName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result joinWar(ServerPlayer player, BlockPos tablePos, String allyFactionName) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result endWar(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result surrenderWar(ServerPlayer player, BlockPos tablePos) {
            return managementUnavailable(player, tablePos);
        }

        @Override
        public Result mapSetClaims(ServerPlayer player, boolean claimed, List<Long> packedChunks) {
            return Result.denied(Component.translatable("kingdoms.error.claims_unavailable"), null);
        }

        private Result managementUnavailable(ServerPlayer player, BlockPos tablePos) {
            return Result.denied(
                    Component.translatable("kingdoms.error.management_unavailable"),
                    view(player, tablePos)
            );
        }
    }

    private FactionServerHooks() {
    }
}
