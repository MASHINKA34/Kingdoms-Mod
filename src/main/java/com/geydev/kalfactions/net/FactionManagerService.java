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
                    "This faction table is already bound to a faction.",
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
                    "Only the faction leader can change faction settings.",
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
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
                    "Your faction role cannot change claims.",
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
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
            return FactionServerHooks.Result.denied("You are not in a faction.", view(player, tablePos));
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
                    view(player, tablePos)
            );
        }
        if (amount <= 0L) {
            return FactionServerHooks.Result.denied("Enter an amount to deposit.", view(player, tablePos));
        }
        if (Long.MAX_VALUE - faction.treasuryBalance() < amount) {
            return FactionServerHooks.Result.denied(
                    "The faction treasury cannot hold that amount.",
                    view(player, tablePos)
            );
        }

        NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
        if (!payment.ready()) {
            return FactionServerHooks.Result.denied(
                    "You only have " + NumismaticsEconomy.format(payment.available()) + ".",
                    view(player, tablePos)
            );
        }
        if (!NumismaticsEconomy.commitPayment(player, payment)) {
            return FactionServerHooks.Result.denied(
                    "Your coin inventory changed; try the deposit again.",
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.deposit(faction.id(), amount);
        if (!result.successful()) {
            NumismaticsEconomy.give(player, amount);
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        String message = "Deposited " + NumismaticsEconomy.format(amount)
                + (payment.change() > 0L
                        ? "; returned " + NumismaticsEconomy.format(payment.change()) + " in change."
                        : ".");
        return new FactionServerHooks.Result(true, message, view(player, tablePos));
    }

    @Override
    public FactionServerHooks.Result withdraw(ServerPlayer player, BlockPos tablePos, long amount) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null) {
            return FactionServerHooks.Result.denied("You are not in a faction.", view(player, tablePos));
        }
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    "Only officers and the leader can withdraw from the treasury.",
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
                    view(player, tablePos)
            );
        }
        if (amount <= 0L) {
            return FactionServerHooks.Result.denied("Enter an amount to withdraw.", view(player, tablePos));
        }
        if (!NumismaticsEconomy.canGive(amount)) {
            return FactionServerHooks.Result.denied(
                    "Withdraw at most " + NumismaticsEconomy.format(NumismaticsEconomy.MAX_SINGLE_PAYOUT)
                            + " at a time.",
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
                "Withdrew " + NumismaticsEconomy.format(amount) + ".",
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result kickMember(ServerPlayer player, BlockPos tablePos, UUID targetId) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        if (faction == null) {
            return FactionServerHooks.Result.denied("You are not in a faction.", view(player, tablePos));
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
                    view(player, tablePos)
            );
        }
        String rejection = validateMemberAction(faction, player.getUUID(), targetId, FactionRole.OFFICER);
        if (rejection != null) {
            return FactionServerHooks.Result.denied(rejection, view(player, tablePos));
        }

        FactionManager.OperationResult result = manager.removeMember(faction.id(), targetId);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(targetId);
        if (target != null) {
            target.sendSystemMessage(Component.literal("You were removed from " + faction.name() + "."));
        }
        return new FactionServerHooks.Result(
                true,
                "Removed " + playerName(player, targetId) + " from the faction.",
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
            return FactionServerHooks.Result.denied("You are not in a faction.", view(player, tablePos));
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
                    view(player, tablePos)
            );
        }
        FactionRole role;
        try {
            role = FactionRole.valueOf(roleName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FactionServerHooks.Result.denied("That role is not valid.", view(player, tablePos));
        }
        if (role == FactionRole.LEADER) {
            return FactionServerHooks.Result.denied(
                    "Transfer leadership with /f transfer instead.",
                    view(player, tablePos)
            );
        }
        String rejection = validateMemberAction(faction, player.getUUID(), targetId, FactionRole.LEADER);
        if (rejection != null) {
            return FactionServerHooks.Result.denied(rejection, view(player, tablePos));
        }

        FactionManager.OperationResult result = manager.setMemberRole(faction.id(), targetId, role);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        return new FactionServerHooks.Result(
                true,
                playerName(player, targetId) + " is now " + roleName(role) + ".",
                view(player, tablePos)
        );
    }

    @Override
    public FactionServerHooks.Result setPvp(ServerPlayer player, BlockPos tablePos, boolean enabled) {
        FactionManager manager = FactionManager.get(player.serverLevel());
        Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
        FactionRole role = manager.getRole(player.getUUID()).orElse(FactionRole.MEMBER);
        if (faction == null) {
            return FactionServerHooks.Result.denied("You are not in a faction.", view(player, tablePos));
        }
        if (!role.isAtLeast(FactionRole.OFFICER)) {
            return FactionServerHooks.Result.denied(
                    "Only officers and the leader can change friendly PvP.",
                    view(player, tablePos)
            );
        }
        if (!canUseBoundTable(player, tablePos, faction.id())) {
            return FactionServerHooks.Result.denied(
                    "This faction table belongs to another faction.",
                    view(player, tablePos)
            );
        }

        FactionManager.OperationResult result = manager.setInternalPvp(faction.id(), enabled);
        if (!result.successful()) {
            return FactionServerHooks.Result.denied(message(result.status()), view(player, tablePos));
        }
        return new FactionServerHooks.Result(
                true,
                "Friendly PvP " + (enabled ? "enabled." : "disabled."),
                view(player, tablePos)
        );
    }

    /**
     * Mirrors the {@code /f} role gating: the actor must hold at least {@code minimumActorRole},
     * cannot target themselves, the target must be a faction member, and only the leader may
     * manage officers. Returns {@code null} when the action is allowed, otherwise a reason.
     */
    private static String validateMemberAction(
            Faction faction,
            UUID actorId,
            UUID targetId,
            FactionRole minimumActorRole
    ) {
        FactionRole actorRole = faction.roleOf(actorId).orElse(null);
        if (actorRole == null || !actorRole.isAtLeast(minimumActorRole)) {
            return "Your faction role cannot do that.";
        }
        if (actorId.equals(targetId)) {
            return "You cannot use this action on yourself.";
        }
        FactionRole targetRole = faction.roleOf(targetId).orElse(null);
        if (targetRole == null) {
            return "That player is not in your faction.";
        }
        if (actorRole != FactionRole.LEADER && targetRole.isAtLeast(FactionRole.OFFICER)) {
            return "Only the faction leader can manage officers.";
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

    private static int colorFor(UUID factionId) {
        int hash = factionId.hashCode();
        int red = 72 + Math.floorMod(hash, 144);
        int green = 72 + Math.floorMod(hash >> 8, 144);
        int blue = 72 + Math.floorMod(hash >> 16, 144);
        return red << 16 | green << 8 | blue;
    }

    private static String message(FactionManager.Status status) {
        return switch (status) {
            case SUCCESS -> "";
            case INVALID_NAME -> "That faction name is invalid.";
            case NAME_TAKEN -> "That faction name is already in use.";
            case PLAYER_ALREADY_MEMBER -> "You already belong to a faction.";
            case CLAIM_ALREADY_OWNED -> "That chunk is already claimed.";
            case CLAIM_NOT_OWNED -> "Your faction does not own that chunk.";
            case CLAIM_NOT_ADJACENT -> "New claims must touch your existing territory.";
            case CLAIM_WOULD_DISCONNECT -> "Releasing that chunk would split your territory.";
            case INSUFFICIENT_FUNDS -> "Your faction treasury cannot afford that claim.";
            case TREASURY_OVERFLOW -> "The refund would overflow the faction treasury.";
            case PLAYER_NOT_MEMBER -> "You are not a member of that faction.";
            case FACTION_NOT_FOUND -> "Faction data could not be found.";
            default -> "The faction action was rejected: " + status.name().toLowerCase(Locale.ROOT) + ".";
        };
    }
}
