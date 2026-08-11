package com.geydev.kalfactions.command;

import com.geydev.kalfactions.blackzone.BlackZoneFormat;
import com.geydev.kalfactions.blackzone.BlackZoneService;
import com.geydev.kalfactions.blackzone.BlackZoneStage;
import com.geydev.kalfactions.blackzone.KolyvanSpawner;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BlackZoneCommands {
    private static final int MAX_MINUTES = 10080;

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("blackzone")
                .then(Commands.literal("status")
                        .executes(context -> status(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> status(
                                        context,
                                        EntityArgument.getPlayer(context, "player")
                                ))))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, MAX_MINUTES))
                                        .executes(BlackZoneCommands::set))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(BlackZoneCommands::clear)))
                .then(Commands.literal("kolyvan")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(BlackZoneCommands::kolyvan)));
    }

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        long accumulated = BlackZoneService.accumulatedMillis(source.getServer(), player.getUUID());
        BlackZoneStage stage = BlackZoneStage.current(accumulated);
        boolean active = accumulated > 0L || BlackZoneService.inBlackZone(player);
        Component stageName = active && stage != null
                ? Component.translatable("kingdoms.blackzone.stage_name." + stage.id())
                : Component.translatable("kingdoms.blackzone.stage_name.none");
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.blackzone.status",
                player.getName(),
                BlackZoneFormat.clock(accumulated),
                stageName,
                BlackZoneService.inBlackZone(player)
                        ? Component.translatable("kingdoms.blackzone.status.inside")
                        : Component.translatable("kingdoms.blackzone.status.outside")
        ), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        BlackZoneService.setAccumulatedMinutes(source.getServer(), player, minutes);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.blackzone.set",
                player.getName(),
                BlackZoneFormat.clock(minutes * BlackZoneStage.MINUTE_MILLIS)
        ), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        BlackZoneService.reset(source.getServer(), player);
        source.sendSuccess(
                () -> Component.translatable("commands.kingdoms.blackzone.cleared", player.getName()),
                true
        );
        return 1;
    }

    private static int kolyvan(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        if (!KolyvanSpawner.spawnNow(player.serverLevel(), player)) {
            source.sendFailure(Component.translatable("commands.kingdoms.blackzone.kolyvan_failed", player.getName()));
            return 0;
        }
        source.sendSuccess(
                () -> Component.translatable("commands.kingdoms.blackzone.kolyvan", player.getName()),
                true
        );
        return 1;
    }

    private BlackZoneCommands() {
    }
}
