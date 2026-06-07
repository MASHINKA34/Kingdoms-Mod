package com.geydev.kalfactions.command;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionManager.OperationResult;
import com.geydev.kalfactions.faction.FactionRole;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class FactionCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("f")
            .requires(source -> CommandPermissions.has(source, CommandPermissions.FACTION))
            .executes(FactionCommands::help)
            .then(Commands.literal("create")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.CREATE))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(FactionCommands::create)))
            .then(Commands.literal("invite")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.MEMBERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(FactionCommands::invite)))
            .then(Commands.literal("join")
                .executes(context -> join(context, null))
                .then(Commands.argument("faction", StringArgumentType.greedyString())
                    .executes(context -> join(context, StringArgumentType.getString(context, "faction")))))
            .then(Commands.literal("decline")
                .executes(context -> decline(context, null))
                .then(Commands.argument("faction", StringArgumentType.greedyString())
                    .executes(context -> decline(context, StringArgumentType.getString(context, "faction")))))
            .then(Commands.literal("leave")
                .executes(FactionCommands::leave))
            .then(Commands.literal("disband")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.SETTINGS))
                .executes(FactionCommands::disband))
            .then(Commands.literal("rename")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.SETTINGS))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(FactionCommands::rename)))
            .then(Commands.literal("kick")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.MEMBERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(FactionCommands::kick)))
            .then(Commands.literal("rank")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.MEMBERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((context, builder) ->
                            SharedSuggestionProvider.suggest(new String[]{"member", "officer"}, builder))
                        .executes(FactionCommands::rank))))
            .then(Commands.literal("role")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.MEMBERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("role", StringArgumentType.word())
                        .suggests((context, builder) ->
                            SharedSuggestionProvider.suggest(new String[]{"member", "officer"}, builder))
                        .executes(FactionCommands::role))))
            .then(Commands.literal("transfer")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.MEMBERS))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(FactionCommands::transfer)))
            .then(Commands.literal("claim")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.CLAIMS))
                .executes(FactionCommands::claim))
            .then(Commands.literal("unclaim")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.CLAIMS))
                .executes(FactionCommands::unclaim))
            .then(Commands.literal("pvp")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.SETTINGS))
                .executes(FactionCommands::pvpStatus)
                .then(Commands.literal("on").executes(context -> setPvp(context, true)))
                .then(Commands.literal("off").executes(context -> setPvp(context, false))))
            .then(Commands.literal("deposit")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.TREASURY))
                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                    .executes(FactionCommands::deposit)))
            .then(Commands.literal("withdraw")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.WITHDRAW))
                .then(Commands.argument("amount", LongArgumentType.longArg(1L))
                    .executes(FactionCommands::withdraw)))
            .then(Commands.literal("info")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.INFO))
                .executes(context -> info(context, null))
                .then(Commands.argument("faction", StringArgumentType.greedyString())
                    .executes(context -> info(context, StringArgumentType.getString(context, "faction")))))
            .then(Commands.literal("map")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.INFO))
                .executes(context -> map(context, 4))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
                    .executes(context -> map(context, IntegerArgumentType.getInteger(context, "radius"))))));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
            "/f create, invite, join, leave, claim, unclaim, deposit, withdraw, info, map"
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OperationResult result = manager(context).createFaction(
            player.getUUID(),
            StringArgumentType.getString(context, "name"),
            ClaimKey.of(player.level(), player.blockPosition())
        );
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Faction created with its starter claims.");
        return Command.SINGLE_SUCCESS;
    }

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Faction faction = ownFaction(context, actor).orElse(null);
        if (faction == null || !requireRole(context, faction, actor, FactionRole.OFFICER)) {
            return 0;
        }
        if (actor.getUUID().equals(target.getUUID())) {
            return failure(context, "You cannot invite yourself.");
        }
        if (manager(context).getFactionForMember(target.getUUID()).isPresent()) {
            return failure(context, "That player already belongs to a faction.");
        }

        PendingFactionInvites.put(
            context.getSource().getServer(),
            faction.id(),
            actor.getUUID(),
            target.getUUID()
        );
        success(context, "Invited " + target.getGameProfile().getName() + " to " + faction.name() + ".");
        target.sendSystemMessage(Component.literal(
            actor.getGameProfile().getName() + " invited you to " + faction.name()
                + ". Use /f join " + faction.name() + " within 5 minutes."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int join(CommandContext<CommandSourceStack> context, String factionName)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FactionManager manager = manager(context);
        if (manager.getFactionForMember(player.getUUID()).isPresent()) {
            return failure(context, "Leave your current faction first.");
        }

        PendingFactionInvites.Invite invite;
        if (factionName == null) {
            invite = PendingFactionInvites.newest(context.getSource().getServer(), player.getUUID()).orElse(null);
        } else {
            Faction requested = manager.getFactionByName(factionName).orElse(null);
            invite = requested == null
                ? null
                : PendingFactionInvites.find(
                    context.getSource().getServer(),
                    requested.id(),
                    player.getUUID()
                ).orElse(null);
        }
        if (invite == null) {
            return failure(context, "No matching, unexpired faction invite was found.");
        }

        OperationResult result = manager.addMember(invite.factionId(), player.getUUID());
        if (!result.successful()) {
            return failure(context, result);
        }
        PendingFactionInvites.remove(context.getSource().getServer(), invite.factionId(), player.getUUID());
        Faction faction = manager.getFaction(invite.factionId()).orElseThrow();
        success(context, "Joined " + faction.name() + ".");
        notifyOnline(context.getSource().getServer(), invite.inviterId(),
            player.getGameProfile().getName() + " joined " + faction.name() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int decline(CommandContext<CommandSourceStack> context, String factionName)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FactionManager manager = manager(context);
        PendingFactionInvites.Invite invite;
        if (factionName == null) {
            invite = PendingFactionInvites.newest(context.getSource().getServer(), player.getUUID()).orElse(null);
        } else {
            Faction faction = manager.getFactionByName(factionName).orElse(null);
            invite = faction == null
                ? null
                : PendingFactionInvites.find(
                    context.getSource().getServer(),
                    faction.id(),
                    player.getUUID()
                ).orElse(null);
        }
        if (invite == null
            || !PendingFactionInvites.remove(
                context.getSource().getServer(),
                invite.factionId(),
                player.getUUID()
            )) {
            return failure(context, "No matching, unexpired faction invite was found.");
        }
        success(context, "Faction invite declined.");
        notifyOnline(context.getSource().getServer(), invite.inviterId(),
            player.getGameProfile().getName() + " declined the faction invite.");
        return Command.SINGLE_SUCCESS;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return 0;
        }
        OperationResult result = manager(context).removeMember(faction.id(), player.getUUID());
        if (!result.successful()) {
            return failure(context, result);
        }
        PendingFactionInvites.removeForPlayer(context.getSource().getServer(), player.getUUID());
        success(context, "You left " + faction.name() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int disband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireLeader(context, faction, player)) {
            return 0;
        }
        if (!NumismaticsEconomy.canGive(faction.treasuryBalance())) {
            return failure(context, "Withdraw the treasury in smaller chunks before disbanding.");
        }
        OperationResult result = manager(context).disbandFaction(faction.id());
        if (!result.successful()) {
            return failure(context, result);
        }
        PendingFactionInvites.removeForFaction(context.getSource().getServer(), faction.id());
        if (result.amount() > 0L) {
            NumismaticsEconomy.give(player, result.amount());
        }
        success(context, "Faction disbanded"
            + (result.amount() > 0L
                ? "; returned " + NumismaticsEconomy.format(result.amount()) + " to the leader."
                : "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int rename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireLeader(context, faction, player)) {
            return 0;
        }
        OperationResult result = manager(context).renameFaction(
            faction.id(),
            StringArgumentType.getString(context, "name")
        );
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Faction renamed.");
        return Command.SINGLE_SUCCESS;
    }

    private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Faction faction = ownFaction(context, actor).orElse(null);
        if (faction == null || !requireRole(context, faction, actor, FactionRole.OFFICER)) {
            return 0;
        }
        if (!canManageTarget(context, faction, actor, target)) {
            return 0;
        }
        OperationResult result = manager(context).removeMember(faction.id(), target.getUUID());
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Removed " + target.getGameProfile().getName() + " from the faction.");
        target.sendSystemMessage(Component.literal("You were removed from " + faction.name() + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int rank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Faction faction = ownFaction(context, actor).orElse(null);
        if (faction == null || !requireLeader(context, faction, actor)) {
            return 0;
        }
        FactionRole rank;
        try {
            rank = FactionRole.valueOf(StringArgumentType.getString(context, "rank").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return failure(context, "Rank must be member or officer.");
        }
        if (rank == FactionRole.LEADER) {
            return failure(context, "Use /f transfer to change the leader.");
        }
        OperationResult result = manager(context).setMemberRole(faction.id(), target.getUUID(), rank);
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, target.getGameProfile().getName() + " is now " + rank.name().toLowerCase(Locale.ROOT) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int role(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Faction faction = ownFaction(context, actor).orElse(null);
        if (faction == null || !requireLeader(context, faction, actor)) {
            return 0;
        }
        if (!canManageTarget(context, faction, actor, target)) {
            return 0;
        }
        FactionRole role;
        try {
            role = FactionRole.valueOf(StringArgumentType.getString(context, "role").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return failure(context, "Role must be member or officer.");
        }
        if (role == FactionRole.LEADER) {
            return failure(context, "Use /f transfer to change the leader.");
        }
        OperationResult result = manager(context).setMemberRole(faction.id(), target.getUUID(), role);
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, target.getGameProfile().getName() + " now has role "
            + role.name().toLowerCase(Locale.ROOT) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int transfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer actor = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        Faction faction = ownFaction(context, actor).orElse(null);
        if (faction == null || !requireLeader(context, faction, actor)) {
            return 0;
        }
        OperationResult result = manager(context).transferLeadership(faction.id(), target.getUUID());
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Leadership transferred to " + target.getGameProfile().getName() + ".");
        target.sendSystemMessage(Component.literal("You are now the leader of " + faction.name() + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int claim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireRole(context, faction, player, FactionRole.OFFICER)) {
            return 0;
        }
        long quoted = manager(context).quoteClaimPrice(faction.id(), player.getUUID());
        OperationResult result = manager(context).claim(
            faction.id(),
            ClaimKey.of(player.level(), player.blockPosition()),
            player.getUUID()
        );
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Chunk claimed for " + NumismaticsEconomy.format(result.amount())
            + (quoted == result.amount() ? "." : " (price changed during execution)."));
        return Command.SINGLE_SUCCESS;
    }

    private static int unclaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireRole(context, faction, player, FactionRole.OFFICER)) {
            return 0;
        }
        OperationResult result = manager(context).unclaim(
            faction.id(),
            ClaimKey.of(player.level(), player.blockPosition())
        );
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Chunk unclaimed; " + NumismaticsEconomy.format(result.amount())
            + " returned to the treasury.");
        return Command.SINGLE_SUCCESS;
    }

    private static int pvpStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return 0;
        }
        boolean enabled = faction.internalPvp();
        success(context, "Friendly PvP is " + (enabled ? "enabled" : "disabled") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int setPvp(CommandContext<CommandSourceStack> context, boolean enabled)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireRole(context, faction, player, FactionRole.OFFICER)) {
            return 0;
        }
        OperationResult result = manager(context).setInternalPvp(faction.id(), enabled);
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "Friendly PvP " + (enabled ? "enabled" : "disabled") + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int deposit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        if (Long.MAX_VALUE - faction.treasuryBalance() < amount) {
            return failure(context, "The faction treasury cannot hold that amount.");
        }

        NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
        if (!payment.ready()) {
            return failure(context, "You only have " + NumismaticsEconomy.format(payment.available()) + ".");
        }
        if (!NumismaticsEconomy.commitPayment(player, payment)) {
            return failure(context, "Your coin inventory changed; try the deposit again.");
        }

        OperationResult result = manager(context).deposit(faction.id(), amount);
        if (!result.successful()) {
            NumismaticsEconomy.give(player, amount);
            return failure(context, result);
        }
        success(context, "Deposited " + NumismaticsEconomy.format(amount)
            + (payment.change() > 0L
                ? "; returned " + NumismaticsEconomy.format(payment.change()) + " in change."
                : "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int withdraw(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireRole(context, faction, player, FactionRole.OFFICER)) {
            return 0;
        }
        long amount = LongArgumentType.getLong(context, "amount");
        if (!NumismaticsEconomy.canGive(amount)) {
            return failure(context, "Withdraw at most "
                + NumismaticsEconomy.format(NumismaticsEconomy.MAX_SINGLE_PAYOUT)
                + " per command.");
        }
        OperationResult result = manager(context).withdraw(faction.id(), amount);
        if (!result.successful()) {
            return failure(context, result);
        }
        NumismaticsEconomy.give(player, amount);
        success(context, "Withdrew " + NumismaticsEconomy.format(amount) + ".");
        return Command.SINGLE_SUCCESS;
    }

    private static int info(CommandContext<CommandSourceStack> context, String factionName)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = factionName == null
            ? manager(context).getFactionForMember(player.getUUID()).orElse(null)
            : manager(context).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            return failure(context, factionName == null
                ? "You are not in a faction."
                : "Faction not found.");
        }

        String ownerName = profileName(context.getSource().getServer(), faction.ownerId());
        MutableComponent message = Component.literal(faction.name()).withStyle(ChatFormatting.GOLD)
            .append(Component.literal(
                "\nLeader: " + ownerName
                    + "\nMembers: " + faction.memberCount()
                    + "\nClaims: " + faction.claimCount()
                    + "\nTreasury: " + NumismaticsEconomy.format(faction.treasuryBalance())
                    + "\nInfluence: " + faction.influence()
            ).withStyle(ChatFormatting.GRAY));
        context.getSource().sendSuccess(() -> message, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int map(CommandContext<CommandSourceStack> context, int radius)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FactionManager manager = manager(context);
        Faction own = manager.getFactionForMember(player.getUUID()).orElse(null);
        ClaimKey center = ClaimKey.of(player.level(), player.blockPosition());
        MutableComponent output = Component.literal("Faction map (" + center.x() + ", " + center.z() + ")\n")
            .withStyle(ChatFormatting.GOLD);

        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if (x == 0 && z == 0) {
                    output.append(Component.literal("@").withStyle(ChatFormatting.YELLOW));
                    continue;
                }
                Faction owner = manager.getFactionAt(center.offset(x, z)).orElse(null);
                if (owner == null) {
                    output.append(Component.literal(".").withStyle(ChatFormatting.DARK_GRAY));
                } else if (own != null && owner.id().equals(own.id())) {
                    output.append(Component.literal("#").withStyle(ChatFormatting.GREEN));
                } else {
                    String symbol = owner.name().substring(0, 1).toUpperCase(Locale.ROOT);
                    output.append(Component.literal(symbol).withStyle(ChatFormatting.RED));
                }
            }
            if (z < radius) {
                output.append(Component.literal("\n"));
            }
        }
        output.append(Component.literal("\n@ you  # your faction  . wilderness").withStyle(ChatFormatting.GRAY));
        context.getSource().sendSuccess(() -> output, false);
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Faction> ownFaction(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player
    ) {
        Optional<Faction> faction = manager(context).getFactionForMember(player.getUUID());
        if (faction.isEmpty()) {
            failure(context, "You are not in a faction.");
        }
        return faction;
    }

    private static boolean requireRole(
        CommandContext<CommandSourceStack> context,
        Faction faction,
        ServerPlayer player,
        FactionRole required
    ) {
        boolean allowed = faction.roleOf(player.getUUID())
            .map(role -> role.isAtLeast(required))
            .orElse(false);
        if (!allowed) {
            failure(context, "This action requires faction role "
                + required.name().toLowerCase(Locale.ROOT) + " or higher.");
        }
        return allowed;
    }

    private static boolean requireLeader(
        CommandContext<CommandSourceStack> context,
        Faction faction,
        ServerPlayer player
    ) {
        return requireRole(context, faction, player, FactionRole.LEADER);
    }

    private static boolean canManageTarget(
        CommandContext<CommandSourceStack> context,
        Faction faction,
        ServerPlayer actor,
        ServerPlayer target
    ) {
        FactionRole actorRank = faction.roleOf(actor.getUUID()).orElse(null);
        FactionRole targetRank = faction.roleOf(target.getUUID()).orElse(null);
        if (targetRank == null) {
            failure(context, "That player is not in your faction.");
            return false;
        }
        if (actor.getUUID().equals(target.getUUID())) {
            failure(context, "You cannot use this action on yourself.");
            return false;
        }
        if (actorRank != FactionRole.LEADER && targetRank.isAtLeast(FactionRole.OFFICER)) {
            failure(context, "Only the faction leader can manage officers.");
            return false;
        }
        return true;
    }

    private static FactionManager manager(CommandContext<CommandSourceStack> context) {
        return FactionManager.get(context.getSource().getServer());
    }

    private static int failure(CommandContext<CommandSourceStack> context, OperationResult result) {
        return failure(context, switch (result.status()) {
            case SUCCESS -> "The operation succeeded.";
            case FACTION_NOT_FOUND -> "Faction not found.";
            case INVALID_NAME -> "Faction names must contain 1-32 printable characters.";
            case INVALID_COLOR -> "The faction color is invalid.";
            case NAME_TAKEN -> "That faction name is already taken.";
            case INVALID_STARTER_SIZE -> "The configured starter claim size is invalid.";
            case PLAYER_ALREADY_MEMBER -> "That player already belongs to a faction.";
            case PLAYER_NOT_MEMBER -> "That player is not a faction member.";
            case OWNER_CANNOT_LEAVE -> "The leader must transfer leadership or disband the faction.";
            case INVALID_ROLE_CHANGE -> "That role change is not allowed.";
            case CLAIM_ALREADY_OWNED -> "That chunk is already claimed.";
            case CLAIM_NOT_OWNED -> "Your faction does not own that chunk.";
            case CLAIM_NOT_ADJACENT -> "The claim must be adjacent to your faction territory.";
            case CLAIM_WOULD_DISCONNECT -> "Unclaiming that chunk would split faction territory.";
            case CHEST_OUTSIDE_TERRITORY -> "That chest is outside faction territory.";
            case INVALID_AMOUNT -> "The amount must be positive.";
            case INSUFFICIENT_FUNDS -> "The faction treasury has insufficient funds.";
            case TREASURY_OVERFLOW -> "The faction treasury cannot hold that amount.";
            case INSUFFICIENT_INFLUENCE -> "The faction has insufficient influence.";
        });
    }

    private static int failure(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static void success(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    private static void notifyOnline(MinecraftServer server, UUID playerId, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private static String profileName(MinecraftServer server, UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return Optional.ofNullable(server.getProfileCache())
            .flatMap(cache -> cache.get(playerId))
            .map(GameProfile::getName)
            .orElse(playerId.toString());
    }

    private FactionCommands() {
    }
}
