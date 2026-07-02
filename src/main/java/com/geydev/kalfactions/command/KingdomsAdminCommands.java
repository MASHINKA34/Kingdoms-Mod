package com.geydev.kalfactions.command;

import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.outpost.trader.TraderService;
import com.geydev.kalfactions.sanctuary.SanctuaryExecutionManager;
import com.geydev.kalfactions.worldmap.WorldMapRenderManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

public final class KingdomsAdminCommands {
    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(ResearchNode.values()).map(ResearchNode::id),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdoms")
                .requires(source -> source.hasPermission(2))
                .then(com.geydev.kalfactions.market.MarketPlotCommands.build())
                .then(Commands.literal("spawntrader")
                        .executes(KingdomsAdminCommands::spawnTrader))
                .then(Commands.literal("sanctuary")
                        .then(Commands.literal("vulnerable")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(KingdomsAdminCommands::makeSanctuaryVulnerable))))
                .then(Commands.literal("research")
                        .then(Commands.literal("complete")
                                .then(Commands.argument("node", StringArgumentType.word())
                                        .suggests(NODE_SUGGESTIONS)
                                        .executes(KingdomsAdminCommands::completeResearch)))
                        .then(Commands.literal("all")
                                .executes(KingdomsAdminCommands::completeAllResearch))
                        .then(Commands.literal("reset")
                                .executes(KingdomsAdminCommands::resetResearch)))
                .then(Commands.literal("map")
                        .then(Commands.literal("render")
                                .executes(context -> startRender(context, DEFAULT_MAP_RESOLUTION))
                                .then(Commands.argument("resolution", IntegerArgumentType.integer(256, 8192))
                                        .executes(context -> startRender(context,
                                                IntegerArgumentType.getInteger(context, "resolution")))))
                        .then(Commands.literal("cancel")
                                .executes(KingdomsAdminCommands::cancelRender))
                        .then(Commands.literal("status")
                                .executes(KingdomsAdminCommands::mapStatus))));
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

    private static int makeSanctuaryVulnerable(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        boolean changed = SanctuaryExecutionManager.get(target.serverLevel())
                .setVulnerableUntilDeath(target.getUUID());
        if (!changed) {
            source.sendSuccess(
                    () -> Component.literal(target.getGameProfile().getName() + " уже уязвим на спавне до смерти."),
                    false
            );
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(target.getGameProfile().getName() + " теперь уязвим на спавне до первой смерти."),
                true
        );
        target.displayClientMessage(Component.literal("Защита спавна отключена до вашей первой смерти."), false);
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

    private static final int DEFAULT_MAP_RESOLUTION = 2048;
    private static final int MAX_MAP_REGION = 20_000;

    private static int startRender(CommandContext<CommandSourceStack> context, int resolution) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (WorldMapRenderManager.isRunning()) {
            source.sendFailure(Component.literal(
                    "Рендер карты уже идёт: " + WorldMapRenderManager.progressPercent() + "%"));
            return 0;
        }
        WorldBorder border = level.getWorldBorder();
        int centerX = (int) Math.round(border.getCenterX());
        int centerZ = (int) Math.round(border.getCenterZ());
        int regionBlocks = (int) Math.min(border.getSize(), MAX_MAP_REGION);
        WorldMapRenderManager.start(level, centerX, centerZ, regionBlocks, resolution);
        source.sendSuccess(() -> Component.literal(
                "Старт рендера карты " + regionBlocks + "x" + regionBlocks
                        + " @ " + resolution + "px вокруг " + centerX + ", " + centerZ + "."), true);
        return 1;
    }

    private static int cancelRender(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!WorldMapRenderManager.cancel()) {
            source.sendFailure(Component.literal("Рендер карты сейчас не выполняется."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Рендер карты отменён."), true);
        return 1;
    }

    private static int mapStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (WorldMapRenderManager.isRunning()) {
            source.sendSuccess(() -> Component.literal(
                    "Рендер карты: " + WorldMapRenderManager.progressPercent() + "%"), false);
        } else {
            source.sendSuccess(() -> Component.literal("Рендер карты не выполняется."), false);
        }
        return 1;
    }

    private KingdomsAdminCommands() {
    }
}
