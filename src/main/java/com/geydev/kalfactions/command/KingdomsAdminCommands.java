package com.geydev.kalfactions.command;

import com.geydev.kalfactions.outpost.trader.TraderService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class KingdomsAdminCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdoms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawntrader")
                        .executes(KingdomsAdminCommands::spawnTrader)));
    }

    private static int spawnTrader(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        if (!TraderService.spawn(level, player.getX(), player.getY(), player.getZ(), player.getYRot())) {
            source.sendFailure(Component.translatable("command.kingdoms.spawntrader.failed"));
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable("command.kingdoms.spawntrader.success"),
                true
        );
        return 1;
    }

    private KingdomsAdminCommands() {
    }
}
