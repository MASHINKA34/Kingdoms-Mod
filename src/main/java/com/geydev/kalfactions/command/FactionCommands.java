package com.geydev.kalfactions.command;

import com.geydev.kalfactions.chest.AccessTool;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.FactionManager.OperationResult;
import com.geydev.kalfactions.faction.FactionRole;
import com.geydev.kalfactions.war.War;
import com.geydev.kalfactions.war.WarManager;
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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
            .then(Commands.literal("chest")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.CLAIMS))
                .executes(FactionCommands::chestInfo)
                .then(Commands.literal("add")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(FactionCommands::chestAdd)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(FactionCommands::chestRemove)))
                .then(Commands.literal("clear")
                    .executes(FactionCommands::chestClear)))
            .then(Commands.literal("info")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.INFO))
                .executes(context -> info(context, null))
                .then(Commands.argument("faction", StringArgumentType.greedyString())
                    .executes(context -> info(context, StringArgumentType.getString(context, "faction")))))
            .then(Commands.literal("map")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.INFO))
                .executes(context -> map(context, 4))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
                    .executes(context -> map(context, IntegerArgumentType.getInteger(context, "radius")))))
            .then(Commands.literal("forceload")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.FACTION))
                .executes(FactionCommands::forceLoadToggle))
            .then(Commands.literal("war")
                .requires(source -> CommandPermissions.has(source, CommandPermissions.FACTION))
                .executes(FactionCommands::warStatus)
                .then(Commands.literal("declare")
                    .requires(source -> CommandPermissions.has(source, CommandPermissions.SETTINGS))
                    .then(Commands.argument("faction", StringArgumentType.greedyString())
                        .executes(FactionCommands::warDeclare)))
                .then(Commands.literal("end")
                    .requires(source -> CommandPermissions.has(source, CommandPermissions.SETTINGS))
                    .executes(FactionCommands::warEnd))
                .then(Commands.literal("surrender")
                    .requires(source -> CommandPermissions.has(source, CommandPermissions.SETTINGS))
                    .executes(FactionCommands::warSurrender))
                .then(Commands.literal("status")
                    .requires(source -> CommandPermissions.has(source, CommandPermissions.INFO))
                    .executes(FactionCommands::warStatus))));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
            () -> Component.translatable("kingdoms.command.faction.help"),
            false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FactionManager manager = manager(context);
        OperationResult result = manager.createFaction(
            player.getUUID(),
            StringArgumentType.getString(context, "name"),
            ClaimKey.of(player.level(), player.blockPosition())
        );
        if (!result.successful()) {
            return failure(context, result);
        }
        success(context, "kingdoms.command.faction.created");
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
            return failure(context, "kingdoms.command.faction.invite.self");
        }
        if (manager(context).getFactionForMember(target.getUUID()).isPresent()) {
            return failure(context, "kingdoms.error.player_already_member");
        }
        if (faction.memberCount() >= FactionManager.MAX_FACTION_MEMBERS) {
            return failure(context, "kingdoms.error.faction_full");
        }

        PendingFactionInvites.PutResult inviteResult = PendingFactionInvites.put(
            context.getSource().getServer(),
            faction.id(),
            actor.getUUID(),
            target.getUUID()
        );
        if (inviteResult == PendingFactionInvites.PutResult.FACTION_FULL) {
            return failure(context, "kingdoms.error.faction_full");
        }
        if (inviteResult != PendingFactionInvites.PutResult.CREATED) {
            return failure(context, "kingdoms.error.faction_not_found");
        }
        com.geydev.kalfactions.net.FactionServerHooks.pushInviteBadge(target);
        success(
            context,
            "kingdoms.command.faction.invite.sent",
            target.getGameProfile().getName(),
            faction.name()
        );
        com.geydev.kalfactions.net.FactionServerHooks.sendNotice(
            target,
            Component.translatable(
                "kingdoms.command.faction.invite.received",
                actor.getGameProfile().getName(),
                faction.name()
            ),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int join(CommandContext<CommandSourceStack> context, String factionName)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FactionManager manager = manager(context);
        if (manager.getFactionForMember(player.getUUID()).isPresent()) {
            return failure(context, "kingdoms.command.faction.join.leave_current_first");
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
            return failure(context, "kingdoms.command.faction.invite.not_found");
        }

        OperationResult result = manager.addMember(invite.factionId(), player.getUUID());
        if (!result.successful()) {
            return failure(context, result);
        }
        PendingFactionInvites.remove(context.getSource().getServer(), invite.factionId(), player.getUUID());
        Faction faction = manager.getFaction(invite.factionId()).orElseThrow();
        success(context, "kingdoms.command.faction.join.success", faction.name());
        notifyOnline(
            context.getSource().getServer(),
            invite.inviterId(),
            "kingdoms.command.faction.join.notice",
            player.getGameProfile().getName(),
            faction.name()
        );
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
            return failure(context, "kingdoms.command.faction.invite.not_found");
        }
        success(context, "kingdoms.command.faction.invite.declined");
        notifyOnline(
            context.getSource().getServer(),
            invite.inviterId(),
            "kingdoms.command.faction.invite.declined_notice",
            player.getGameProfile().getName()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return 0;
        }
        if (faction.ownerId().equals(player.getUUID()) && faction.memberCount() == 1) {
            if (!NumismaticsEconomy.canGive(faction.treasuryBalance())) {
                return failure(context, "kingdoms.command.faction.disband.withdraw_first");
            }
            OperationResult disbanded = manager(context).disbandFaction(faction.id());
            if (!disbanded.successful()) {
                return failure(context, disbanded);
            }
            PendingFactionInvites.removeForFaction(context.getSource().getServer(), faction.id());
            if (disbanded.amount() > 0L) {
                NumismaticsEconomy.give(player, disbanded.amount());
                success(
                    context,
                    "kingdoms.command.faction.leave.disbanded_refund",
                    faction.name(),
                    NumismaticsEconomy.format(disbanded.amount())
                );
            } else {
                success(context, "kingdoms.command.faction.leave.disbanded", faction.name());
            }
            return Command.SINGLE_SUCCESS;
        }
        OperationResult result = manager(context).removeMember(faction.id(), player.getUUID());
        if (!result.successful()) {
            return failure(context, result);
        }
        PendingFactionInvites.removeForPlayer(context.getSource().getServer(), player.getUUID());
        success(context, "kingdoms.command.faction.leave.success", faction.name());
        return Command.SINGLE_SUCCESS;
    }

    private static int disband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireLeader(context, faction, player)) {
            return 0;
        }
        if (!NumismaticsEconomy.canGive(faction.treasuryBalance())) {
            return failure(context, "kingdoms.command.faction.disband.withdraw_first");
        }
        OperationResult result = manager(context).disbandFaction(faction.id());
        if (!result.successful()) {
            return failure(context, result);
        }
        PendingFactionInvites.removeForFaction(context.getSource().getServer(), faction.id());
        if (result.amount() > 0L) {
            NumismaticsEconomy.give(player, result.amount());
        }
        if (result.amount() > 0L) {
            success(
                context,
                "kingdoms.command.faction.disband.success_refund",
                NumismaticsEconomy.format(result.amount())
            );
        } else {
            success(context, "kingdoms.command.faction.disband.success");
        }
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
        success(context, "kingdoms.command.faction.rename.success");
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
        success(
            context,
            "kingdoms.command.faction.member.removed",
            target.getGameProfile().getName()
        );
        target.sendSystemMessage(Component.translatable(
            "kingdoms.command.faction.member.removed_notice",
            faction.name()
        ));
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
            return failure(context, "kingdoms.command.faction.role.invalid");
        }
        if (rank == FactionRole.LEADER) {
            return failure(context, "kingdoms.command.faction.leadership.use_transfer");
        }
        OperationResult result = manager(context).setMemberRole(faction.id(), target.getUUID(), rank);
        if (!result.successful()) {
            return failure(context, result);
        }
        success(
            context,
            "kingdoms.command.faction.role.rank_changed",
            target.getGameProfile().getName(),
            roleName(rank)
        );
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
            return failure(context, "kingdoms.command.faction.role.invalid");
        }
        if (role == FactionRole.LEADER) {
            return failure(context, "kingdoms.command.faction.leadership.use_transfer");
        }
        OperationResult result = manager(context).setMemberRole(faction.id(), target.getUUID(), role);
        if (!result.successful()) {
            return failure(context, result);
        }
        success(
            context,
            "kingdoms.command.faction.role.changed",
            target.getGameProfile().getName(),
            roleName(role)
        );
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
        success(
            context,
            "kingdoms.command.faction.leadership.transferred",
            target.getGameProfile().getName()
        );
        target.sendSystemMessage(Component.translatable(
            "kingdoms.command.faction.leadership.received",
            faction.name()
        ));
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
        success(
            context,
            quoted == result.amount()
                ? "kingdoms.command.faction.claim.success"
                : "kingdoms.command.faction.claim.success_price_changed",
            NumismaticsEconomy.format(result.amount())
        );
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
        success(
            context,
            "kingdoms.command.faction.unclaim.success",
            NumismaticsEconomy.format(result.amount())
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int chestInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = lookedAtBlock(context, player);
        if (pos == null) {
            return 0;
        }
        return report(context, AccessTool.describe(player, player.serverLevel(), pos));
    }

    private static int chestAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        BlockPos pos = lookedAtBlock(context, player);
        if (pos == null) {
            return 0;
        }
        return report(context, AccessTool.addWhitelistPlayer(
            player,
            player.serverLevel(),
            pos,
            target.getUUID(),
            target.getGameProfile().getName()
        ));
    }

    private static int chestRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        BlockPos pos = lookedAtBlock(context, player);
        if (pos == null) {
            return 0;
        }
        return report(context, AccessTool.removeWhitelistPlayer(
            player,
            player.serverLevel(),
            pos,
            target.getUUID(),
            target.getGameProfile().getName()
        ));
    }

    private static int chestClear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = lookedAtBlock(context, player);
        if (pos == null) {
            return 0;
        }
        return report(context, AccessTool.clearWhitelist(player, player.serverLevel(), pos));
    }

    private static BlockPos lookedAtBlock(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange() + 1.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            failure(context, "kingdoms.chest.not_container");
            return null;
        }
        return ((BlockHitResult) hit).getBlockPos();
    }

    private static int report(CommandContext<CommandSourceStack> context, AccessTool.WhitelistResult result) {
        if (result.success()) {
            context.getSource().sendSuccess(result::message, false);
            return Command.SINGLE_SUCCESS;
        }
        context.getSource().sendFailure(result.message());
        return 0;
    }

    private static int pvpStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return 0;
        }
        boolean enabled = faction.internalPvp();
        success(context, "kingdoms.command.faction.pvp.status", enabledState(enabled));
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
        success(context, "kingdoms.command.faction.pvp.status", enabledState(enabled));
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
            return failure(context, "kingdoms.error.treasury_overflow");
        }

        NumismaticsEconomy.Payment payment = NumismaticsEconomy.preparePayment(player, amount);
        if (!payment.ready()) {
            return failure(
                context,
                "kingdoms.error.available_funds",
                NumismaticsEconomy.format(payment.available())
            );
        }
        if (!NumismaticsEconomy.commitPayment(player, payment)) {
            return failure(context, "kingdoms.error.coin_inventory_changed");
        }

        OperationResult result = manager(context).deposit(faction.id(), amount);
        if (!result.successful()) {
            NumismaticsEconomy.give(player, amount);
            return failure(context, result);
        }
        if (payment.change() > 0L) {
            success(
                context,
                "kingdoms.command.faction.deposit.success_change",
                NumismaticsEconomy.format(amount),
                NumismaticsEconomy.format(payment.change())
            );
        } else {
            success(
                context,
                "kingdoms.command.faction.deposit.success",
                NumismaticsEconomy.format(amount)
            );
        }
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
            return failure(
                context,
                "kingdoms.command.faction.withdraw.max",
                NumismaticsEconomy.format(NumismaticsEconomy.MAX_SINGLE_PAYOUT)
            );
        }
        OperationResult result = manager(context).withdraw(faction.id(), amount);
        if (!result.successful()) {
            return failure(context, result);
        }
        NumismaticsEconomy.give(player, amount);
        success(
            context,
            "kingdoms.command.faction.withdraw.success",
            NumismaticsEconomy.format(amount)
        );
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
                ? "kingdoms.error.not_in_faction"
                : "kingdoms.error.faction_not_found");
        }

        String ownerName = profileName(context.getSource().getServer(), faction.ownerId());
        MutableComponent message = Component.literal(faction.name()).withStyle(ChatFormatting.GOLD)
            .append(Component.translatable(
                "kingdoms.command.faction.info.details",
                ownerName,
                faction.memberCount(),
                faction.claimCount(),
                NumismaticsEconomy.format(faction.treasuryBalance()),
                faction.influence()
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
        MutableComponent output = Component.translatable(
            "kingdoms.command.faction.map.title",
            center.x(),
            center.z()
        ).withStyle(ChatFormatting.GOLD).append("\n");

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
        output.append(Component.translatable(
            "kingdoms.command.faction.map.legend"
        ).withStyle(ChatFormatting.GRAY));
        context.getSource().sendSuccess(() -> output, false);
        return Command.SINGLE_SUCCESS;
    }

    private static int warDeclare(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FactionManager manager = manager(context);
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireLeader(context, faction, player)) {
            return 0;
        }
        Faction target = manager.getFactionByName(StringArgumentType.getString(context, "faction")).orElse(null);
        if (target == null) {
            return failure(context, "kingdoms.error.faction_not_found");
        }

        MinecraftServer server = context.getSource().getServer();
        WarManager.DeclareResult result = WarManager.get(server).declareWar(
            server,
            faction.id(),
            target.id(),
            com.geydev.kalfactions.war.WarType.DEFAULT,
            "",
            server.overworld().getGameTime()
        );
        switch (result) {
            case SUCCESS -> {
                success(context, "kingdoms.command.faction.war.declared", target.name());
                return Command.SINGLE_SUCCESS;
            }
            case SAME_FACTION -> {
                return failure(context, "kingdoms.command.faction.war.same_faction");
            }
            case ATTACKER_BUSY -> {
                return failure(context, "kingdoms.command.faction.war.attacker_busy");
            }
            case ATTACKER_COOLDOWN -> {
                return failure(
                    context,
                    "kingdoms.command.faction.war.cooldown",
                    WarManager.get(server).declareCooldownRemainingHours(faction.id())
                );
            }
            case DEFENDER_BUSY -> {
                return failure(context, "kingdoms.command.faction.war.defender_busy", target.name());
            }
            case DEFENDER_OFFLINE -> {
                return failure(context, "kingdoms.command.faction.war.defender_offline", target.name());
            }
            default -> {
                return 0;
            }
        }
    }

    private static int warEnd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireLeader(context, faction, player)) {
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        Optional<UUID> opponent = WarManager.get(server).endWarForFaction(server, faction.id());
        if (opponent.isEmpty()) {
            return failure(context, "kingdoms.command.faction.war.not_active");
        }
        success(context, "kingdoms.command.faction.war.ended");
        return Command.SINGLE_SUCCESS;
    }

    private static int forceLoadToggle(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return failure(context, "kingdoms.error.not_in_faction");
        }
        MinecraftServer server = context.getSource().getServer();
        ClaimKey key = ClaimKey.of(player.serverLevel(), player.blockPosition());
        com.geydev.kalfactions.faction.FactionManager manager =
            com.geydev.kalfactions.faction.FactionManager.get(server);
        com.geydev.kalfactions.faction.FactionManager.ForceLoadResult result =
            manager.toggleForceLoad(server, faction.id(), key);
        switch (result) {
            case ENABLED -> {
                success(context, "kingdoms.command.faction.forceload.enabled");
                return Command.SINGLE_SUCCESS;
            }
            case DISABLED -> {
                success(context, "kingdoms.command.faction.forceload.disabled");
                return Command.SINGLE_SUCCESS;
            }
            case LIMIT_REACHED -> {
                return failure(
                    context,
                    "kingdoms.command.faction.forceload.limit",
                    manager.forceLoadLimit(faction.id())
                );
            }
            case NOT_OWN_CLAIM -> {
                return failure(context, "kingdoms.command.faction.forceload.not_own");
            }
            default -> {
                return failure(context, "kingdoms.error.not_in_faction");
            }
        }
    }

    private static int warSurrender(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null || !requireLeader(context, faction, player)) {
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        Optional<UUID> opponent = WarManager.get(server).surrender(server, faction.id());
        if (opponent.isEmpty()) {
            return failure(context, "kingdoms.command.faction.war.not_active");
        }
        success(context, "kingdoms.command.faction.war.surrendered");
        return Command.SINGLE_SUCCESS;
    }

    private static int warStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Faction faction = ownFaction(context, player).orElse(null);
        if (faction == null) {
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        War war = WarManager.get(server).warForFaction(faction.id()).orElse(null);
        if (war == null) {
            success(context, "kingdoms.command.faction.war.not_at_war");
            return Command.SINGLE_SUCCESS;
        }

        UUID opponentId = war.opponentOf(faction.id());
        Component opponentName = opponentId == null
            ? Component.translatable("kingdoms.command.faction.war.opponent.unknown")
            : manager(context).getFactionById(opponentId)
                .<Component>map(opponent -> Component.literal(opponent.name()))
                .orElseGet(() -> Component.translatable(
                    "kingdoms.command.faction.war.opponent.disbanded"
                ));
        long elapsedTicks = Math.max(0L, server.overworld().getGameTime() - war.startGameTime());
        Component role = Component.translatable(war.attackerFactionId().equals(faction.id())
            ? "kingdoms.command.faction.war.role.attacker"
            : "kingdoms.command.faction.war.role.defender");
        Component stateLabel = Component.translatable(switch (war.state()) {
            case ACTIVE -> "kingdoms.command.faction.war.state.active";
            case ENDING -> "kingdoms.command.faction.war.state.ending";
            case ENDED -> "kingdoms.command.faction.war.state.ended";
            case DECLARED -> "kingdoms.command.faction.war.state.declared";
        });
        MutableComponent message = Component.translatable(
            "kingdoms.command.faction.war.status.title"
        ).withStyle(ChatFormatting.GOLD).append(Component.translatable(
            "kingdoms.command.faction.war.status.details",
            opponentName,
            role,
            stateLabel,
            elapsedTicks / 20L,
            war.snapshotCount()
        ).withStyle(ChatFormatting.GRAY));
        context.getSource().sendSuccess(() -> message, false);
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<Faction> ownFaction(
        CommandContext<CommandSourceStack> context,
        ServerPlayer player
    ) {
        Optional<Faction> faction = manager(context).getFactionForMember(player.getUUID());
        if (faction.isEmpty()) {
            failure(context, "kingdoms.error.not_in_faction");
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
            failure(
                context,
                "kingdoms.error.required_role",
                roleName(required)
            );
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
            failure(context, "kingdoms.error.target_not_in_faction");
            return false;
        }
        if (actor.getUUID().equals(target.getUUID())) {
            failure(context, "kingdoms.error.self_action");
            return false;
        }
        if (actorRank != FactionRole.LEADER && targetRank.isAtLeast(FactionRole.OFFICER)) {
            failure(context, "kingdoms.error.manage_officers");
            return false;
        }
        return true;
    }

    private static FactionManager manager(CommandContext<CommandSourceStack> context) {
        return FactionManager.get(context.getSource().getServer());
    }

    private static int failure(CommandContext<CommandSourceStack> context, OperationResult result) {
        return failure(context, switch (result.status()) {
            case SUCCESS -> "kingdoms.operation.success";
            case FACTION_NOT_FOUND -> "kingdoms.error.faction_not_found";
            case INVALID_NAME -> "kingdoms.error.invalid_name";
            case INVALID_COLOR -> "kingdoms.error.invalid_color";
            case NAME_TAKEN -> "kingdoms.error.name_taken";
            case INVALID_STARTER_SIZE -> "kingdoms.error.invalid_starter_size";
            case PLAYER_ALREADY_MEMBER -> "kingdoms.error.player_already_member";
            case FACTION_FULL -> "kingdoms.error.faction_full";
            case PLAYER_NOT_MEMBER -> "kingdoms.error.player_not_member";
            case OWNER_CANNOT_LEAVE -> "kingdoms.error.owner_cannot_leave";
            case INVALID_ROLE_CHANGE -> "kingdoms.error.invalid_role_change";
            case CLAIM_ALREADY_OWNED -> "kingdoms.error.claim_already_owned";
            case CLAIM_NOT_OWNED -> "kingdoms.error.claim_not_owned";
            case CLAIM_NOT_ADJACENT -> "kingdoms.error.claim_not_adjacent";
            case CLAIM_WOULD_DISCONNECT -> "kingdoms.error.claim_would_disconnect";
            case CLAIM_PROTECTED -> "kingdoms.error.claim_protected";
            case CHEST_OUTSIDE_TERRITORY -> "kingdoms.error.chest_outside_territory";
            case INVALID_AMOUNT -> "kingdoms.error.invalid_amount";
            case INSUFFICIENT_FUNDS -> "kingdoms.error.insufficient_funds";
            case TREASURY_OVERFLOW -> "kingdoms.error.treasury_overflow";
            case INSUFFICIENT_INFLUENCE -> "kingdoms.error.insufficient_influence";
            case INVALID_ALLIANCE -> "kingdoms.error.invalid_alliance";
            case NOT_ALLIED -> "kingdoms.error.not_allied";
            case OUTPOST_CHUNK -> "kingdoms.error.outpost_chunk";
        });
    }

    private static int failure(
        CommandContext<CommandSourceStack> context,
        String translationKey,
        Object... args
    ) {
        context.getSource().sendFailure(Component.translatable(translationKey, args));
        return 0;
    }

    private static void success(
        CommandContext<CommandSourceStack> context,
        String translationKey,
        Object... args
    ) {
        context.getSource().sendSuccess(
            () -> Component.translatable(translationKey, args),
            false
        );
    }

    private static void notifyOnline(
        MinecraftServer server,
        UUID playerId,
        String translationKey,
        Object... args
    ) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(translationKey, args));
        }
    }

    private static Component enabledState(boolean enabled) {
        return Component.translatable(enabled
            ? "kingdoms.state.enabled"
            : "kingdoms.state.disabled");
    }

    private static Component roleName(FactionRole role) {
        return Component.translatable("kingdoms.role." + role.name().toLowerCase(Locale.ROOT));
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
