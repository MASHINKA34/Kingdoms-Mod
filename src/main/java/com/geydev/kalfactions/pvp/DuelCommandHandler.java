package com.geydev.kalfactions.pvp;

import com.geydev.kalfactions.KalFactions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class DuelCommandHandler {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> duel = Commands.literal("duel")
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
                        )));
        event.getDispatcher().register(duel);
    }

    private static int request(ServerPlayer challenger, ServerPlayer target) {
        return report(challenger, DuelManager.request(challenger, target));
    }

    private static int report(ServerPlayer player, DuelManager.Result result) {
        if (result == DuelManager.Result.SUCCESS) {
            return Command.SINGLE_SUCCESS;
        }

        String message = switch (result) {
            case SELF -> "You cannot duel yourself.";
            case ALREADY_ACTIVE -> "That duel is already active.";
            case NOT_FOUND -> "No matching duel request was found.";
            case PLAYER_OFFLINE -> "The challenger is offline.";
            case SUCCESS -> throw new IllegalStateException("Handled above");
        };
        player.sendSystemMessage(Component.literal(message));
        return 0;
    }

    private DuelCommandHandler() {
    }
}
