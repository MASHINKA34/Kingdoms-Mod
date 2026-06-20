package com.geydev.kalfactions.command;

import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.outpost.trader.TraderService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class KingdomsAdminCommands {
    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(ResearchNode.values()).map(ResearchNode::id),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdoms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawntrader")
                        .executes(KingdomsAdminCommands::spawnTrader))
                .then(Commands.literal("research")
                        .then(Commands.literal("complete")
                                .then(Commands.argument("node", StringArgumentType.word())
                                        .suggests(NODE_SUGGESTIONS)
                                        .executes(KingdomsAdminCommands::completeResearch)))
                        .then(Commands.literal("all")
                                .executes(KingdomsAdminCommands::completeAllResearch))
                        .then(Commands.literal("reset")
                                .executes(KingdomsAdminCommands::resetResearch))));
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

    private static int completeResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            source.sendFailure(Component.literal("Вы не состоите во фракции."));
            return 0;
        }
        String nodeId = StringArgumentType.getString(context, "node");
        ResearchNode node = ResearchNode.parse(nodeId).orElse(null);
        if (node == null) {
            source.sendFailure(Component.literal("Неизвестное исследование: " + nodeId));
            return 0;
        }
        boolean changed = manager.grantResearch(factionId, node);
        source.sendSuccess(
                () -> Component.literal((changed ? "Изучено: " : "Уже изучено: ") + node.id()),
                true
        );
        return 1;
    }

    private static int completeAllResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            source.sendFailure(Component.literal("Вы не состоите во фракции."));
            return 0;
        }
        int granted = 0;
        for (ResearchNode node : ResearchNode.values()) {
            if (manager.grantResearch(factionId, node)) {
                granted++;
            }
        }
        int total = granted;
        source.sendSuccess(() -> Component.literal("Изучено узлов: " + total), true);
        return 1;
    }

    private static int resetResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            source.sendFailure(Component.literal("Вы не состоите во фракции."));
            return 0;
        }
        manager.clearAllResearch(factionId);
        source.sendSuccess(() -> Component.literal("Все исследования фракции сброшены."), true);
        return 1;
    }

    private KingdomsAdminCommands() {
    }
}
