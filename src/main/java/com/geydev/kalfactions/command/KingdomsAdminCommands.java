package com.geydev.kalfactions.command;

import com.geydev.kalfactions.dimension.DimensionControlEvents;
import com.geydev.kalfactions.dimension.DimensionControlManager;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.news.NewsManager;
import com.geydev.kalfactions.news.NewsService;
import com.geydev.kalfactions.outpost.trader.TraderService;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.sanctuary.SanctuaryExecutionManager;
import com.geydev.kalfactions.worldmap.WorldMapRenderManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.core.BlockPos;

public final class KingdomsAdminCommands {
    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(ResearchNode.values()).map(ResearchNode::id),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> FACTION_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    FactionManager.get(context.getSource().getServer()).factions().stream()
                            .map(com.geydev.kalfactions.faction.Faction::name),
                    builder
            );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kingdoms")
                .requires(source -> source.hasPermission(2))
                .then(com.geydev.kalfactions.market.MarketPlotCommands.build())
                .then(Commands.literal("spawntrader")
                        .executes(KingdomsAdminCommands::spawnTrader))
                .then(Commands.literal("trader")
                        .then(Commands.literal("points")
                                .then(Commands.literal("list").executes(KingdomsAdminCommands::traderPointsList))
                                .then(Commands.literal("add").executes(KingdomsAdminCommands::traderPointsAdd))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(KingdomsAdminCommands::traderPointsRemove)))))
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
                .then(Commands.literal("faction")
                        .then(Commands.literal("move")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .executes(KingdomsAdminCommands::moveFaction))))
                .then(Commands.literal("dimension")
                        .then(dimensionBranch("nether", Level.NETHER, "Ад"))
                        .then(dimensionBranch("end", Level.END, "Энд")))
                .then(Commands.literal("news")
                        .then(Commands.literal("publish")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(KingdomsAdminCommands::publishNews))))
                        .then(Commands.literal("list")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .executes(KingdomsAdminCommands::listNews))))
                .then(Commands.literal("resource")
                        .then(Commands.literal("zone").executes(KingdomsAdminCommands::resourceZone))
                        .then(Commands.literal("stats").executes(KingdomsAdminCommands::resourceStats))
                        .then(Commands.literal("nearest").executes(KingdomsAdminCommands::resourceNearest))
                        .then(Commands.literal("next")
                                .then(Commands.literal("confirm").executes(KingdomsAdminCommands::resourceNext)))
                        .then(Commands.literal("pause").executes(context -> resourcePause(context, true)))
                        .then(Commands.literal("resume").executes(context -> resourcePause(context, false)))
                        .then(Commands.literal("verify").executes(KingdomsAdminCommands::resourceVerify)))
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

    private static int resourceZone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = source.getPosition().x - spawn.getX();
        double dz = source.getPosition().z - spawn.getZ();
        double distance = Math.hypot(dx, dz);
        int blue = com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt();
        int yellow = Math.max(blue, com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_YELLOW_RADIUS.getAsInt());
        ResourceZone zone = ResourceZone.fromDistance(distance, blue, yellow);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.zone",
                zone.name(),
                String.format(java.util.Locale.ROOT, "%.1f", distance),
                spawn.getX(),
                spawn.getZ()
        ), false);
        return 1;
    }

    private static int resourceStats(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceClusterManager.ResourceStatistics stats = ResourceClusterManager.get(source.getLevel()).statistics();
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.stats",
                stats.cycleId(),
                stats.active(),
                stats.depleted(),
                stats.byZone().toString(),
                stats.byResource().toString()
        ), false);
        return 1;
    }

    private static int resourceNearest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos origin = BlockPos.containing(source.getPosition());
        ResourceClusterManager.OreDepositView deposit = ResourceClusterManager.get(source.getLevel())
                .nearestDeposit(origin, 20_000)
                .orElse(null);
        if (deposit == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.resource.nearest.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.nearest",
                deposit.resource().id(),
                deposit.center().getX(),
                deposit.center().getY(),
                deposit.center().getZ(),
                deposit.remaining(),
                deposit.originalReserve(),
                deposit.zone().name(),
                deposit.state()
        ), false);
        return 1;
    }

    private static int resourceNext(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long cycle = ResourceClusterManager.get(source.getLevel()).advanceResourceCycle(System.currentTimeMillis());
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.resource.next", cycle), true);
        return 1;
    }

    private static int resourcePause(CommandContext<CommandSourceStack> context, boolean paused) {
        CommandSourceStack source = context.getSource();
        ResourceClusterManager.get(source.getLevel()).setResourceQueuePaused(paused);
        source.sendSuccess(() -> Component.translatable(
                paused ? "commands.kingdoms.resource.paused" : "commands.kingdoms.resource.resumed"
        ), true);
        return 1;
    }

    private static int resourceVerify(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceClusterManager.IntegrityReport report = ResourceClusterManager.get(source.getLevel()).verifyIntegrity();
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.verify",
                report.deposits(),
                report.trackedBlocks(),
                report.generationQueued(),
                report.cleanupQueued(),
                report.issues()
        ), false);
        return report.issues() == 0 ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> dimensionBranch(
            String literal,
            ResourceKey<Level> dimension,
            String displayName
    ) {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(literal)
                .then(Commands.literal("open")
                        .executes(context -> setDimensionClosed(context, dimension, displayName, false)))
                .then(Commands.literal("close")
                        .executes(context -> setDimensionClosed(context, dimension, displayName, true)))
                .then(Commands.literal("status")
                        .executes(context -> dimensionStatus(context, dimension, displayName)))
                .then(Commands.literal("wipe")
                        .executes(context -> scheduleDimensionWipe(context, dimension, displayName))
                        .then(Commands.literal("cancel")
                                .executes(context -> cancelDimensionWipe(context, dimension, displayName))));
        if (Level.NETHER.equals(dimension)) {
            branch.then(Commands.literal("portal")
                    .then(Commands.literal("set")
                            .then(Commands.argument("from", BlockPosArgument.blockPos())
                                    .then(Commands.argument("to", BlockPosArgument.blockPos())
                                            .executes(KingdomsAdminCommands::setNetherPortal))))
                    .then(Commands.literal("clear").executes(KingdomsAdminCommands::clearNetherPortal))
                    .then(Commands.literal("status").executes(KingdomsAdminCommands::netherPortalStatus)));
        }
        return branch;
    }

    private static int setNetherPortal(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!Level.OVERWORLD.equals(source.getLevel().dimension())) {
            source.sendFailure(Component.translatable("commands.kingdoms.nether.portal.overworld_only"));
            return 0;
        }
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        DimensionControlManager.PortalBounds bounds = new DimensionControlManager.PortalBounds(
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()
        );
        if (!validPortalBounds(source.getLevel(), bounds)) {
            source.sendFailure(Component.translatable("commands.kingdoms.nether.portal.invalid"));
            return 0;
        }
        DimensionControlManager.get(source.getServer()).setNetherPortal(bounds);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.nether.portal.set",
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        ), true);
        return 1;
    }

    private static boolean validPortalBounds(ServerLevel level, DimensionControlManager.PortalBounds bounds) {
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeY = bounds.maxY() - bounds.minY() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        if (sizeX > 16 || sizeY > 16 || sizeZ > 16 || (long) sizeX * sizeY * sizeZ > 4096L) {
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        double centerX = (bounds.minX() + bounds.maxX()) * 0.5D;
        double centerZ = (bounds.minZ() + bounds.maxZ()) * 0.5D;
        if (Math.hypot(centerX - spawn.getX(), centerZ - spawn.getZ()) > 64.0D) {
            return false;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.NETHER_PORTAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static int clearNetherPortal(CommandContext<CommandSourceStack> context) {
        DimensionControlManager.get(context.getSource().getServer()).clearNetherPortal();
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.kingdoms.nether.portal.cleared"), true
        );
        return 1;
    }

    private static int netherPortalStatus(CommandContext<CommandSourceStack> context) {
        DimensionControlManager.PortalBounds bounds = DimensionControlManager.get(context.getSource().getServer())
                .netherPortal().orElse(null);
        if (bounds == null) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("commands.kingdoms.nether.portal.missing"), false
            );
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable(
                "commands.kingdoms.nether.portal.status",
                bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ()
        ), false);
        return 1;
    }

    private static int setDimensionClosed(
            CommandContext<CommandSourceStack> context,
            ResourceKey<Level> dimension,
            String displayName,
            boolean closed
    ) {
        CommandSourceStack source = context.getSource();
        DimensionControlManager control = DimensionControlManager.get(source.getServer());
        if (!control.setClosed(dimension, closed)) {
            source.sendFailure(Component.literal(displayName + (closed ? " уже закрыт." : " уже открыт.")));
            return 0;
        }
        if (!closed) {
            source.sendSuccess(() -> Component.literal(displayName + " открыт."), true);
            return 1;
        }
        int moved = DimensionControlEvents.evacuate(source.getServer(), dimension);
        source.sendSuccess(
                () -> Component.literal(displayName + " закрыт. Игроков перемещено на спавн: " + moved + "."),
                true
        );
        return 1;
    }

    private static int scheduleDimensionWipe(
            CommandContext<CommandSourceStack> context,
            ResourceKey<Level> dimension,
            String displayName
    ) {
        CommandSourceStack source = context.getSource();
        DimensionControlManager control = DimensionControlManager.get(source.getServer());
        if (!control.setWipePending(dimension, true)) {
            source.sendFailure(Component.literal("Вайп уже запланирован: " + displayName
                    + " будет очищен при следующем запуске сервера."));
            return 0;
        }
        int moved = DimensionControlEvents.evacuate(source.getServer(), dimension);
        String hint = control.isClosed(dimension)
                ? ""
                : " Совет: закройте измерение до рестарта — /kingdoms dimension "
                        + dimension.location().getPath().replace("the_", "") + " close.";
        source.sendSuccess(
                () -> Component.literal(displayName + " будет очищен при следующем запуске сервера."
                        + " Игроков перемещено на спавн: " + moved + "." + hint),
                true
        );
        return 1;
    }

    private static int cancelDimensionWipe(
            CommandContext<CommandSourceStack> context,
            ResourceKey<Level> dimension,
            String displayName
    ) {
        CommandSourceStack source = context.getSource();
        DimensionControlManager control = DimensionControlManager.get(source.getServer());
        if (!control.setWipePending(dimension, false)) {
            source.sendFailure(Component.literal("Вайп " + displayName + " не запланирован."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Вайп отменён: " + displayName + " не будет очищен."), true);
        return 1;
    }

    private static int dimensionStatus(
            CommandContext<CommandSourceStack> context,
            ResourceKey<Level> dimension,
            String displayName
    ) {
        CommandSourceStack source = context.getSource();
        DimensionControlManager control = DimensionControlManager.get(source.getServer());
        ServerLevel level = source.getServer().getLevel(dimension);
        int inside = level == null ? 0 : level.players().size();
        source.sendSuccess(
                () -> Component.literal(displayName + ": " + (control.isClosed(dimension) ? "закрыт" : "открыт")
                        + "; вайп при следующем запуске: " + (control.isWipePending(dimension) ? "да" : "нет")
                        + "; игроков внутри: " + inside + "."),
                false
        );
        return 1;
    }

    private static int publishNews(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String factionName = StringArgumentType.getString(context, "faction");
        com.geydev.kalfactions.faction.Faction faction =
                FactionManager.get(source.getServer()).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
            return 0;
        }
        String text = StringArgumentType.getString(context, "text");
        int separator = text.indexOf('|');
        if (separator <= 0 || separator >= text.length() - 1) {
            source.sendFailure(Component.literal("Формат: /kingdoms news publish <фракция> <заголовок>|<текст>"));
            return 0;
        }
        String title = text.substring(0, separator).strip();
        String body = text.substring(separator + 1).strip();
        if (!NewsService.adminPublish(source.getServer(), faction, title, body, source.getTextName())) {
            source.sendFailure(Component.literal("Заголовок и текст не могут быть пустыми."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Новость опубликована от фракции " + faction.name() + "."), true);
        return 1;
    }

    private static int listNews(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String factionName = StringArgumentType.getString(context, "faction");
        com.geydev.kalfactions.faction.Faction faction =
                FactionManager.get(source.getServer()).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
            return 0;
        }
        java.util.List<NewsManager.Article> articles = NewsManager.get(source.getServer()).articles(faction.id());
        if (articles.isEmpty()) {
            source.sendSuccess(() -> Component.literal("У фракции " + faction.name() + " нет новостей."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Новостей у " + faction.name() + ": " + articles.size()), false);
        for (NewsManager.Article article : articles) {
            source.sendSuccess(() -> Component.literal("- [" + article.publishedAtMillis() + "] "
                    + article.title() + " (" + article.author() + ")"), false);
        }
        return articles.size();
    }

    private static int moveFaction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        FactionManager manager = FactionManager.get(player.serverLevel());
        String name = StringArgumentType.getString(context, "name");
        com.geydev.kalfactions.faction.Faction faction = manager.getFactionByName(name).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + name));
            return 0;
        }
        com.geydev.kalfactions.war.WarManager wars = com.geydev.kalfactions.war.WarManager.get(source.getServer());
        if (wars.warForFaction(faction.id()).filter(com.geydev.kalfactions.war.War::isActive).isPresent()) {
            source.sendFailure(Component.literal("Фракция в активной войне — сначала завершите войну."));
            return 0;
        }
        ResourceKey<Level> targetDimension = player.level().dimension();
        java.util.Set<com.geydev.kalfactions.claim.ClaimKey> sanctuaryChunks = java.util.Set.copyOf(
                com.geydev.kalfactions.sanctuary.SanctuaryManager.get(player.serverLevel())
                        .claimsIn(targetDimension));
        FactionManager.RelocateResult result = manager.relocateFaction(
                source.getServer(),
                faction.id(),
                targetDimension,
                player.chunkPosition(),
                sanctuaryChunks::contains
        );
        switch (result.status()) {
            case FACTION_NOT_FOUND -> {
                source.sendFailure(Component.literal("Фракция не найдена: " + name));
                return 0;
            }
            case NO_CLAIMS -> {
                source.sendFailure(Component.literal("У фракции нет клеймов для переноса."));
                return 0;
            }
            case OBSTRUCTED -> {
                source.sendFailure(Component.literal(
                        "Место занято: в целевой области чужие клеймы или спавн. Отойдите и повторите."));
                return 0;
            }
            case SUCCESS -> {
            }
        }
        com.geydev.kalfactions.tax.LagTaxManager.get(source.getServer())
                .relocateChunkLoads(faction.id(), result.mapping());
        manager.reconcileForceLoads(source.getServer());
        com.geydev.kalfactions.integration.IntegrationManager.refreshFromServer(source.getServer());
        com.geydev.kalfactions.net.ClaimSyncManager.resyncAll(source.getServer());
        Component notice = Component.literal(
                "Территория фракции перенесена администратором. Проверьте карту — постройки нужно перевозить самим.");
        for (UUID memberId : faction.members().keySet()) {
            ServerPlayer member = source.getServer().getPlayerList().getPlayer(memberId);
            if (member != null) {
                member.sendSystemMessage(notice);
                com.geydev.kalfactions.net.FactionServerHooks.sendNotice(member, notice, true);
            }
        }
        int moved = result.mapping().size();
        source.sendSuccess(() -> Component.literal(
                "Перенесено чанков: " + moved + " → центр [" + player.chunkPosition().x * 16 + ", "
                        + player.chunkPosition().z * 16 + "] " + targetDimension.location().getPath()
                        + ". Казна, влияние, исследования и таймеры прогрузки сохранены."), true);
        return 1;
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

    private static int traderPointsList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        java.util.List<TraderWorldData.SpawnPoint> points = TraderWorldData.get(source.getServer()).points();
        source.sendSuccess(() -> Component.translatable("kingdoms.trader.point.list.header", points.size()), false);
        int index = 0;
        for (TraderWorldData.SpawnPoint point : points) {
            index++;
            int displayIndex = index;
            source.sendSuccess(() -> Component.translatable(
                    "kingdoms.trader.point.list.entry",
                    displayIndex,
                    point.id(),
                    point.dimension().location(),
                    point.pos().getX(),
                    point.pos().getY(),
                    point.pos().getZ(),
                    String.format(java.util.Locale.ROOT, "%.1f", point.yaw())
            ), false);
        }
        return points.size();
    }

    private static int traderPointsAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TraderWorldData.AddPointResult result = TraderWorldData.get(context.getSource().getServer()).addPoint(
                player.level().dimension(), player.blockPosition(), player.getYRot()
        );
        if (!result.added()) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.point.limit"));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("kingdoms.trader.point.added", result.point().id()), true
        );
        return 1;
    }

    private static int traderPointsRemove(CommandContext<CommandSourceStack> context) {
        UUID id;
        try {
            id = UUID.fromString(StringArgumentType.getString(context, "id"));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.point.invalid_id"));
            return 0;
        }
        if (!TraderWorldData.get(context.getSource().getServer()).removePoint(id)) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.point.not_found", id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("kingdoms.trader.point.removed", id), true);
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
