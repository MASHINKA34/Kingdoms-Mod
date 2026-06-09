package com.geydev.kalfactions.net;

import com.geydev.kalfactions.block.FactionTableBlockEntity;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.command.NumismaticsEconomy;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionMember;
import com.geydev.kalfactions.faction.FactionRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

final class FactionManagerService implements FactionServerHooks.Service {
    private static final int MAP_RADIUS = 6;

    @Override
    public FactionSnapshot view(ServerPlayer player, BlockPos tablePos) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        ChunkPos center = new ChunkPos(tablePos);
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionSnapshot.empty(tablePos, center.x, center.z);
        }

        FactionRole role = faction.roleOf(player.getUUID()).orElse(FactionRole.MEMBER);
        int ownColor = tableColor(player, tablePos, faction.id());
        return new FactionSnapshot(
                tablePos,
                faction.id(),
                faction.name(),
                playerName(player, faction.ownerId()),
                ownColor,
                role == FactionRole.LEADER,
                role.canManageClaims(),
                center.x,
                center.z,
                MAP_RADIUS,
                members(player, faction),
                claims(player, manager, faction.id(), ownColor, center),
                faction.treasuryBalance(),
                faction.influence(),
                faction.internalPvp(),
                player.getUUID(),
                role.isAtLeast(FactionRole.OFFICER)
        );
    }

    @Override
    public FactionServerHooks.Result create(
            ServerPlayer player,
            BlockPos tablePos,
            String name,
            int color
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        if (boundFactionId(player, tablePos) != null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_already_bound"),
                    view(player, tablePos)
            );
        }
        FactionManager.OperationResult result = manager.createFaction(
                player.getUUID(),
                name,
                ClaimKey.of(player.serverLevel(), new ChunkPos(tablePos))
        );
        if (result.successful()) {
            updateTableMetadata(player, tablePos, result.factionId(), color);
        }
        return result(result, player, tablePos);
    }

    @Override
    public FactionServerHooks.Result update(
            ServerPlayer player,
            BlockPos tablePos,
            String name,
            int color
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null || !faction.ownerId().equals(player.getUUID())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.leader_settings_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = faction.name().equals(name)
                ? new FactionManager.OperationResult(FactionManager.Status.SUCCESS, faction.id(), 0L)
                : manager.renameFaction(faction.id(), name);
        if (result.successful()) {
            updateTableMetadata(player, tablePos, faction.id(), color);
        }
        return result(result, player, tablePos);
    }

    @Override
    public FactionServerHooks.Result setClaim(
            ServerPlayer player,
            BlockPos tablePos,
            ChunkPos chunkPos,
            boolean claimed
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null || !role.canManageClaims()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.role_cannot_change_claims"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }

        ClaimKey key = ClaimKey.of(player.serverLevel(), chunkPos);
        FactionManager.OperationResult result = claimed
                ? manager.claim(faction.id(), key, player.getUUID())
                : manager.unclaim(faction.id(), key);
        if (result.successful()) {
            updateTableMetadata(player, tablePos, faction.id(), tableColor(player, tablePos, faction.id()));
        }
        return result(result, player, tablePos);
    }

    @Override
    public FactionServerHooks.Result deposit(ServerPlayer player, BlockPos tablePos, long amount) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        if (amount <= 0L) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.deposit_amount"),
                    view(player, tablePos)
            );
        }
        if (Long.MAX_VALUE - faction.treasuryBalance() < amount) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.treasury_overflow"),
                    view(player, tablePos)
            );
        }

        NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
        if (!payment.ready()) {
            return FactionServerHooks.Result.denied(
                    Component.translatable(
                            "kingdoms.error.available_funds",
                            NumismaticsEconomy.format(payment.available())
                    ),
                    view(player, tablePos)
            );
        }
        if (!NumismaticsEconomy.commitPayment(player, payment)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.coin_inventory_changed"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.deposit(faction.id(), amount);
        if (!result.successful()) {
            NumismaticsEconomy.give(player, amount);
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        Component message = payment.change() > 0L
                ? Component.translatable(
                        "kingdoms.command.faction.deposit.success_change",
                        NumismaticsEconomy.format(amount),
                        NumismaticsEconomy.format(payment.change())
                )
                : Component.translatable(
                        "kingdoms.command.faction.deposit.success",
                        NumismaticsEconomy.format(amount)
                );
        return new FactionServerHooks.Result(true, message, view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.withdraw_officer_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        if (amount <= 0L) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.withdraw_amount"),
                    view(player, tablePos)
            );
        }
        if (!NumismaticsEconomy.canGive(amount)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable(
                            "kingdoms.command.faction.withdraw.max",
                            NumismaticsEconomy.format(NumismaticsEconomy.MAX_SINGLE_PAYOUT)
                    ),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.withdraw(faction.id(), amount);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        NumismaticsEconomy.give(player, amount);
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.withdraw.success",
                        NumismaticsEconomy.format(amount)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        Component rejection = validateMemberAction(faction, player.getUUID(), targetId, FactionRole.OFFICER);
        if (rejection != null) {
            return FactionServerHooks.Result.denied(rejection, view(player, tablePos));
        }

        FactionManager.OperationResult result = manager.removeMember(faction.id(), targetId);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            target.sendSystemMessage(Component.translatable(
                    "kingdoms.command.faction.member.removed_notice",
                    faction.name()
            ));
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.member.removed",
                        playerName(player, targetId)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result setMemberRole(
            ServerPlayer player,
            BlockPos tablePos,
            UUID targetId,
            String roleName
    ) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }
        FactionRole role;
        try {
            role = FactionRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.invalid_role"),
                    view(player, tablePos)
            );
        }
        if (role == FactionRole.LEADER) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.command.faction.leadership.use_transfer"),
                    view(player, tablePos)
            );
        }
        Component rejection = validateMemberAction(faction, player.getUUID(), targetId, FactionRole.LEADER);
        if (rejection != null) {
            return FactionServerHooks.Result.denied(rejection, view(player, tablePos));
        }

        FactionManager.OperationResult result = manager.setMemberRole(faction.id(), targetId, role);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.role.rank_changed",
                        playerName(player, targetId),
                        roleComponent(role)
                ),
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.not_in_faction"),
                    view(player, tablePos)
            );
        }
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.pvp_officer_only"),
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    Component.translatable("kingdoms.error.table_other_faction"),
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.setInternalPvp(faction.id(), enabled);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        return new FactionServerHooks.Result(
                true,
                Component.translatable(
                        "kingdoms.command.faction.pvp.status",
                        enabledState(enabled)
                ),
                view(player, tablePos)
        );
    }

    /**
     * Mirrors the {@code /f} role gating: the actor must hold at least {@code minimumActorRole},
     * cannot target themselves, the target must be a faction member, and only the leader may
     * manage officers. Returns {@code null} when the action is allowed, otherwise a reason.
     */
    private static Component validateMemberAction(
            Faction faction,
            UUID actorId,
            UUID targetId,
            FactionRole minimumActorRole
    ) {
        FactionRole actorRole = faction.roleOf(actorId).orElse(null);
        if (actorRole == null || !actorRole.isAtLeast(minimumActorRole)) {
            return Component.translatable("kingdoms.error.role_cannot_manage_members");
        }
        if (actorId.equals(targetId)) {
            return Component.translatable("kingdoms.error.self_action");
        }
        FactionRole targetRole = faction.roleOf(targetId).orElse(null);
        if (targetRole == null) {
            return Component.translatable("kingdoms.error.target_not_in_faction");
        }
        if (actorRole != FactionRole.LEADER && targetRole.isAtLeast(FactionRole.OFFICER)) {
            return Component.translatable("kingdoms.error.manage_officers");
        }
        return null;
    }

    private FactionServerHooks.Result result(
            FactionManager.OperationResult operation,
            ServerPlayer player,
            BlockPos tablePos
    ) {
        FactionSnapshot snapshot = view(player, tablePos);
        if (operation.successful()) {
            return FactionServerHooks.Result.success(snapshot);
        }
        return FactionServerHooks.Result.denied(message(operation.status()), snapshot);
    }

    private static List<FactionSnapshot.Member> members(ServerPlayer player, Faction faction) {
        return faction.members().values().stream()
                .sorted(Comparator
                        .comparing((FactionMember member) -> member.role().ordinal()).reversed()
                        .thenComparing(member -> playerName(player, member.playerId()), String.CASE_INSENSITIVE_ORDER))
                .map(member -> new FactionSnapshot.Member(
                        member.playerId(),
                        playerName(player, member.playerId()),
                        roleName(member.role())
                ))
                .toList();
    }

    private static List<FactionSnapshot.Claim> claims(
            ServerPlayer player,
            FactionManager manager,
            UUID ownFactionId,
            int ownColor,
            ChunkPos center
    ) {
        List<FactionSnapshot.Claim> claims = new ArrayList<>();
        for (int x = center.x - MAP_RADIUS; x <= center.x + MAP_RADIUS; x++) {
            for (int z = center.z - MAP_RADIUS; z <= center.z + MAP_RADIUS; z++) {
                Faction faction = manager
                        .getFactionAt(player.level().dimension(), new ChunkPos(x, z))
                        .orElse(null);
                if (faction == null) {
                    continue;
                }
                boolean own = faction.id().equals(ownFactionId);
                claims.add(new FactionSnapshot.Claim(
                        x,
                        z,
                        own ? ownColor : colorFor(faction.id()),
                        faction.name(),
                        own
                ));
            }
        }
        return List.copyOf(claims);
    }

    private static int tableColor(ServerPlayer player, BlockPos tablePos, UUID factionId) {
        if (player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity table
                && factionId.equals(table.getFactionId())) {
            return table.getFactionColor();
        }
        return colorFor(factionId);
    }

    private static boolean canUseBoundTable(ServerPlayer player, BlockPos tablePos, UUID factionId) {
        UUID boundFactionId = boundFactionId(player, tablePos);
        return boundFactionId == null || boundFactionId.equals(factionId);
    }

    private static UUID boundFactionId(ServerPlayer player, BlockPos tablePos) {
        if (player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity table) {
            return table.getFactionId();
        }
        return null;
    }

    private static void updateTableMetadata(
            ServerPlayer player,
            BlockPos tablePos,
            UUID factionId,
            int color
    ) {
        if (player.level().getBlockEntity(tablePos) instanceof FactionTableBlockEntity table) {
            table.setFactionId(factionId);
            table.setFactionColor(color);
        }
    }

    private static String playerName(ServerPlayer player, UUID playerId) {
        ServerPlayer online = player.getServer().getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return player.getServer().getProfileCache()
                .get(playerId)
                .map(profile -> profile.getName())
                .orElse(playerId.toString().substring(0, 8));
    }

    private static String roleName(FactionRole role) {
        String lower = role.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static Component roleComponent(FactionRole role) {
        return Component.translatable("kingdoms.role." + role.name().toLowerCase(Locale.ROOT));
    }

    private static Component enabledState(boolean enabled) {
        return Component.translatable(enabled
                ? "kingdoms.state.enabled"
                : "kingdoms.state.disabled");
    }

    private static int colorFor(UUID factionId) {
        int hash = factionId.hashCode();
        int red = 72 + Math.floorMod(hash, 144);
        int green = 72 + Math.floorMod(hash >> 8, 144);
        int blue = 72 + Math.floorMod(hash >> 16, 144);
        return red << 16 | green << 8 | blue;
    }

    private static Component message(FactionManager.Status status) {
        String key = switch (status) {
            case SUCCESS -> null;
            case INVALID_NAME -> "kingdoms.error.invalid_name";
            case NAME_TAKEN -> "kingdoms.error.name_taken";
            case PLAYER_ALREADY_MEMBER -> "kingdoms.error.already_in_faction";
            case CLAIM_ALREADY_OWNED -> "kingdoms.error.claim_already_owned";
            case CLAIM_NOT_OWNED -> "kingdoms.error.claim_not_owned";
            case CLAIM_NOT_ADJACENT -> "kingdoms.error.claim_not_adjacent";
            case CLAIM_WOULD_DISCONNECT -> "kingdoms.error.claim_would_disconnect";
            case INSUFFICIENT_FUNDS -> "kingdoms.error.insufficient_funds";
            case TREASURY_OVERFLOW -> "kingdoms.error.treasury_overflow";
            case PLAYER_NOT_MEMBER -> "kingdoms.error.not_member_of_faction";
            case FACTION_NOT_FOUND -> "kingdoms.error.faction_data_not_found";
            default -> "kingdoms.error.faction_action_rejected";
        };
        if (key == null) {
            return Component.empty();
        }
        return Component.translatable(key);
    }
}
