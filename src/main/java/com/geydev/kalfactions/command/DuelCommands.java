package com.geydev.kalfactions.command;

import com.geydev.kalfactions.pvp.DuelManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DuelCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("duel")
            .requires(source -> CommandPermissions.has(source, CommandPermissions.DUEL))
            .then(Commands.literal("request")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> request(
                        context.getSource().getPlayerOrException(),
                        EntityArgument.getPlayer(context, "player")
                    ))))
            .then(Commands.literal("accept")
                .executes(context -> report(
                    context.getSource().getPlayerOrException(),
                    DuelManager.accept(context.getSource().getPlayerOrException())
                ))
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> report(
                        context.getSource().getPlayerOrException(),
                        DuelManager.accept(
                            context.getSource().getPlayerOrException(),
                            EntityArgument.getPlayer(context, "player")
                        )
                    ))))
            .then(Commands.literal("decline")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> report(
                        context.getSource().getPlayerOrException(),
                        DuelManager.decline(
                            context.getSource().getPlayerOrException(),
                            EntityArgument.getPlayer(context, "player")
                        )
                    ))))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(context -> request(
                    context.getSource().getPlayerOrException(),
                    EntityArgument.getPlayer(context, "player")
                ))));
    }

    private static int request(ServerPlayer challenger, ServerPlayer target) {
        return report(challenger, DuelManager.request(challenger, target));
    }

    private static int report(ServerPlayer player, DuelManager.Result result) {
        if (result == DuelManager.Result.SUCCESS) {
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.translatable(switch (result) {
            case SELF -> "kingdoms.duel.error.self";
            case ALREADY_ACTIVE -> "kingdoms.duel.error.already_active";
            case NOT_FOUND -> "kingdoms.duel.error.not_found";
            case PLAYER_OFFLINE -> "kingdoms.duel.error.player_offline";
            case SUCCESS -> throw new IllegalStateException("Handled above");
        }));
        return 0;
    }

    private DuelCommands() {
    }
}
