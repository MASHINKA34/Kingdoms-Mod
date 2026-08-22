package com.geydev.kalfactions.command;

import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.dimension.DimensionControlEvents;
import com.geydev.kalfactions.dimension.DimensionControlManager;
import com.geydev.kalfactions.dimension.NetherPortalIgnition;
import com.geydev.kalfactions.dimension.NetherSchedulePolicy;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.ResearchNode;
import com.geydev.kalfactions.faction.ScienceLedger;
import com.geydev.kalfactions.news.NewsManager;
import com.geydev.kalfactions.news.NewsService;
import com.geydev.kalfactions.outpost.trader.TraderLifecycle;
import com.geydev.kalfactions.outpost.trader.TraderService;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import com.geydev.kalfactions.outpost.cluster.ResourceClusterManager;
import com.geydev.kalfactions.outpost.cluster.distribution.ResourceZone;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.sanctuary.SanctuaryExecutionManager;
import com.geydev.kalfactions.scout.ScoutManager;
import com.geydev.kalfactions.scout.ScoutOrder;
import com.geydev.kalfactions.scout.ScoutService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Collection;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;

public final class KingdomsAdminCommands {
    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(ResearchNode.values()).map(ResearchNode::id),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> BONUS_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    com.geydev.kalfactions.faction.FactionBonus.SELECTABLE.stream()
                            .map(bonus -> bonus.name().toLowerCase(java.util.Locale.ROOT)),
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
                .then(com.geydev.kalfactions.faith.FaithCommands.build())
                .then(com.geydev.kalfactions.music.MusicCommands.build())
                .then(Commands.literal("spawntrader")
                        .executes(KingdomsAdminCommands::spawnTrader))
                .then(Commands.literal("trader")
                        .then(Commands.literal("points")
                                .then(Commands.literal("list").executes(KingdomsAdminCommands::traderPointsList))
                                .then(Commands.literal("add").executes(KingdomsAdminCommands::traderPointsAdd))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(KingdomsAdminCommands::traderPointsRemove))))
                        .then(Commands.literal("contraband")
                                .then(Commands.literal("spawn").executes(KingdomsAdminCommands::spawnContraband)))
                        .then(Commands.literal("wandering")
                                .then(Commands.literal("spawn")
                                        .executes(KingdomsAdminCommands::spawnWandering)
                                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                                                .suggests(FACTION_SUGGESTIONS)
                                                .executes(KingdomsAdminCommands::spawnWandering)))))
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
                                        .executes(KingdomsAdminCommands::moveFaction)))
                        .then(Commands.literal("bonuses")
                                .then(Commands.argument("first", StringArgumentType.word())
                                        .suggests(BONUS_SUGGESTIONS)
                                        .then(Commands.argument("second", StringArgumentType.word())
                                                .suggests(BONUS_SUGGESTIONS)
                                                .executes(KingdomsAdminCommands::setFactionBonuses)))))
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
                        .then(Commands.literal("chunk")
                                .executes(context -> resourceChunk(context, new ChunkPos(
                                        BlockPos.containing(context.getSource().getPosition())
                                )))
                                .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                        .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                .executes(context -> resourceChunk(
                                                        context,
                                                        new ChunkPos(
                                                                IntegerArgumentType.getInteger(context, "chunkX"),
                                                                IntegerArgumentType.getInteger(context, "chunkZ")
                                                        )
                                                )))))
                        )
                .then(Commands.literal("quarry")
                        .then(Commands.literal("create").executes(KingdomsAdminCommands::quarryCreate))
                        .then(Commands.literal("list").executes(KingdomsAdminCommands::quarryList))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(KingdomsAdminCommands::quarryRemove))))
                .then(Commands.literal("scout")
                        .then(Commands.literal("spawn")
                                .executes(KingdomsAdminCommands::scoutSpawn))
                        .then(Commands.literal("status")
                                .executes(context -> scoutStatus(context, null))
                                .then(Commands.argument("faction", StringArgumentType.greedyString())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .executes(context -> scoutStatus(
                                                context,
                                                StringArgumentType.getString(context, "faction")
                                        ))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("faction", StringArgumentType.greedyString())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .executes(KingdomsAdminCommands::scoutCancel)))
                        .then(Commands.literal("complete")
                                .then(Commands.argument("faction", StringArgumentType.greedyString())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .executes(KingdomsAdminCommands::scoutComplete))))
                .then(Commands.literal("influence")
                        .then(Commands.literal("discoveries")
                                .executes(context -> discoveries(context, null))
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                                                .suggests(FACTION_SUGGESTIONS)
                                                .executes(KingdomsAdminCommands::resetDiscoveries)))
                                .then(Commands.argument("faction", StringArgumentType.greedyString())
                                        .suggests(FACTION_SUGGESTIONS)
                                        .executes(context -> discoveries(
                                                context,
                                                StringArgumentType.getString(context, "faction")
                                        ))))
                        .then(Commands.literal("daily")
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                                                .suggests(FACTION_SUGGESTIONS)
                                                .executes(KingdomsAdminCommands::resetDailyScience)))))
                .then(ClusterCommands.build())
                .then(BlackZoneCommands.build())
                .then(com.geydev.kalfactions.dungeon.DungeonCommands.build()));
    }

    private static int discoveries(CommandContext<CommandSourceStack> context, String factionName) {
        CommandSourceStack source = context.getSource();
        FactionManager factions = FactionManager.get(source.getServer());
        Collection<Faction> targets;
        if (factionName == null) {
            targets = factions.factions();
        } else {
            Faction faction = factions.getFactionByName(factionName).orElse(null);
            if (faction == null) {
                source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
                return 0;
            }
            targets = java.util.List.of(faction);
        }
        ScienceLedger ledger = ScienceLedger.get(source.getServer());
        long now = System.currentTimeMillis();
        long cap = ModConfigSpec.SCIENCE_DAILY_CAP.getAsLong();
        String capText = cap > 0L ? String.valueOf(cap) : "без лимита";
        for (Faction faction : targets) {
            String line = faction.name()
                    + " · открыто предметов: " + ledger.discoveryCount(faction.id())
                    + " · наука за сегодня: " + ledger.grantedToday(faction.id(), now) + "/" + capText;
            source.sendSuccess(() -> Component.literal(line), false);
        }
        if (targets.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Фракций нет."), false);
        }
        return targets.size();
    }

    private static int resetDiscoveries(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String factionName = StringArgumentType.getString(context, "faction");
        Faction faction = FactionManager.get(source.getServer()).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
            return 0;
        }
        ScienceLedger.get(source.getServer()).resetDiscoveries(faction.id());
        source.sendSuccess(() -> Component.literal("Открытия фракции " + faction.name() + " сброшены."), true);
        return 1;
    }

    private static int resetDailyScience(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String factionName = StringArgumentType.getString(context, "faction");
        Faction faction = FactionManager.get(source.getServer()).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
            return 0;
        }
        ScienceLedger.get(source.getServer()).resetDaily(faction.id());
        source.sendSuccess(
                () -> Component.literal("Дневной счётчик науки фракции " + faction.name() + " сброшен."),
                true
        );
        return 1;
    }

    private static int scoutSpawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        float facing = net.minecraft.util.Mth.wrapDegrees(player.getYRot() + 180.0F);
        if (!ScoutService.spawn(level, player.getX(), player.getY(), player.getZ(), facing)) {
            source.sendFailure(Component.literal("Не удалось создать разведчика карт."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Разведчик карт создан."), true);
        return 1;
    }

    private static int scoutStatus(CommandContext<CommandSourceStack> context, String factionName) {
        CommandSourceStack source = context.getSource();
        FactionManager factions = FactionManager.get(source.getServer());
        ScoutManager scouts = ScoutManager.get(source.getServer());
        java.util.List<java.util.Map.Entry<UUID, ScoutOrder>> active = scouts.activeOrders();
        UUID filter = null;
        if (factionName != null) {
            Faction faction = factions.getFactionByName(factionName).orElse(null);
            if (faction == null) {
                source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
                return 0;
            }
            filter = faction.id();
        }
        int shown = 0;
        for (java.util.Map.Entry<UUID, ScoutOrder> entry : active) {
            if (filter != null && !filter.equals(entry.getKey())) {
                continue;
            }
            ScoutOrder order = entry.getValue();
            String name = factions.getFactionById(entry.getKey()).map(Faction::name).orElse(entry.getKey().toString());
            String line = name
                    + " · " + order.sizeChunks() + "x" + order.sizeChunks()
                    + " · центр " + order.centerChunkX() + ", " + order.centerChunkZ()
                    + " · " + order.dimension().location()
                    + " · разведано " + order.progressPercent() + "%"
                    + " · осталось " + ScoutService.formatRemaining(order.remainingMillis(System.currentTimeMillis()))
                    + (order.scanned() ? " · данные готовы" : "");
            source.sendSuccess(() -> Component.literal(line), false);
            shown++;
        }
        if (shown == 0) {
            source.sendSuccess(() -> Component.literal("Активных заказов разведки нет."), false);
        }
        return shown;
    }

    private static int scoutCancel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String factionName = StringArgumentType.getString(context, "faction");
        Faction faction = FactionManager.get(source.getServer()).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
            return 0;
        }
        if (!ScoutService.cancel(source.getServer(), faction.id())) {
            source.sendFailure(Component.literal("У фракции нет активного заказа разведки."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Заказ разведки отменён, деньги возвращены в казну."), true);
        return 1;
    }

    private static int scoutComplete(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String factionName = StringArgumentType.getString(context, "faction");
        Faction faction = FactionManager.get(source.getServer()).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
            return 0;
        }
        if (!ScoutService.completeNow(source.getServer(), faction.id())) {
            source.sendFailure(Component.literal("У фракции нет активного заказа разведки."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Заказ разведки завершён досрочно."), true);
        return 1;
    }

    private static int quarryCreate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        QuarryManager.CreateResult result =
                QuarryManager.get(level).createAtChunk(
                        level,
                        new net.minecraft.world.level.ChunkPos(BlockPos.containing(source.getPosition()))
                );
        if (result != QuarryManager.CreateResult.CREATED) {
            source.sendFailure(Component.translatable(
                    "kingdoms.command.quarry.create_failed." + result.name().toLowerCase(java.util.Locale.ROOT)
            ));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("kingdoms.command.quarry.created"), true);
        return 1;
    }

    private static int quarryList(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Collection<com.geydev.kalfactions.quarry.QuarryManager.QuarryView> quarries =
                QuarryManager.get(source.getServer()).all();
        source.sendSuccess(() -> Component.translatable("kingdoms.command.quarry.count", quarries.size()), false);
        for (com.geydev.kalfactions.quarry.QuarryManager.QuarryView quarry : quarries) {
            source.sendSuccess(() -> Component.literal(
                    quarry.core().getX() + " " + quarry.core().getY() + " " + quarry.core().getZ()
                            + " · level " + quarry.level()
                            + " · owner " + (quarry.ownerFactionId() == null ? "neutral" : quarry.ownerFactionId())
            ), false);
        }
        return quarries.size();
    }

    private static int quarryRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!QuarryManager.get(source.getServer()).removeByCore(source.getLevel(), pos)) {
            source.sendFailure(Component.translatable("kingdoms.command.quarry.not_found"));
            return 0;
        }
        source.getLevel().removeBlock(pos, false);
        source.sendSuccess(() -> Component.translatable("kingdoms.command.quarry.removed"), true);
        return 1;
    }

    private static int resourceZone(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = source.getPosition().x - spawn.getX();
        double dz = source.getPosition().z - spawn.getZ();
        double distance = Math.max(Math.abs(dx), Math.abs(dz));
        int blue = com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_BLUE_RADIUS.getAsInt();
        int yellow = Math.max(blue, com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_YELLOW_RADIUS.getAsInt());
        int red = Math.max(yellow, com.geydev.kalfactions.config.ModConfigSpec.RESOURCE_RED_RADIUS.getAsInt());
        ResourceZone zone = ResourceZone.fromDistance(distance, blue, yellow, red);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.zone",
                zone.name(),
                String.format(java.util.Locale.ROOT, "%.1f", distance),
                spawn.getX(),
                spawn.getZ()
        ), false);
        return 1;
    }

    private static int resourceChunk(CommandContext<CommandSourceStack> context, ChunkPos chunk) {
        CommandSourceStack source = context.getSource();
        ResourceClusterManager.ChunkDiagnostic diagnostic =
                ResourceClusterManager.get(source.getLevel()).diagnoseChunk(source.getLevel(), chunk);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.chunk.zone",
                chunk.x,
                chunk.z,
                diagnostic.zone().name(),
                diagnostic.oreVeinSize()
        ), false);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.resource.chunk.surface",
                diagnostic.surfacePosition() == null ? "-" : diagnostic.surfacePosition().toShortString(),
                diagnostic.surfaceType() == null ? "-" : diagnostic.surfaceType().id(),
                diagnostic.surfaceReason(),
                diagnostic.pendingChunks(),
                diagnostic.knownClusters()
        ), false);
        return 1;
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
                        .executes(context -> dimensionStatus(context, dimension, displayName)));
        if (Level.NETHER.equals(dimension)) {
            branch.then(Commands.literal("portal")
                    .then(Commands.literal("clear").executes(KingdomsAdminCommands::clearNetherPortal))
                    .then(Commands.literal("ignite").executes(KingdomsAdminCommands::igniteNetherPortal))
                    .then(Commands.literal("extinguish").executes(KingdomsAdminCommands::extinguishNetherPortal))
                    .then(Commands.literal("status").executes(KingdomsAdminCommands::netherPortalStatus)));
        } else {
            branch.then(Commands.literal("wipe")
                    .executes(context -> scheduleDimensionWipe(context, dimension, displayName))
                    .then(Commands.literal("cancel")
                            .executes(context -> cancelDimensionWipe(context, dimension, displayName))));
        }
        return branch;
    }

    private static int clearNetherPortal(CommandContext<CommandSourceStack> context) {
        DimensionControlManager.get(context.getSource().getServer()).clearNetherPortal();
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.kingdoms.nether.portal.cleared"), true
        );
        return 1;
    }

    private static int igniteNetherPortal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            source.sendFailure(Component.translatable("commands.kingdoms.nether.portal.overworld_only"));
            return 0;
        }
        BlockPos anchor = NetherPortalIgnition.findAnchor(level, BlockPos.containing(source.getPosition()))
                .or(() -> DimensionControlManager.get(source.getServer()).netherPortalAnchor()
                        .filter(stored -> level.getBlockState(stored).is(ModBlocks.NETHER_PORTAL_ANCHOR.get())))
                .orElse(null);
        if (anchor == null) {
            source.sendFailure(Component.translatable(
                    "commands.kingdoms.nether.portal.anchor_not_found", NetherPortalIgnition.ANCHOR_SEARCH_RADIUS
            ));
            return 0;
        }
        NetherPortalIgnition.Result result =
                NetherPortalIgnition.ignite(level, anchor, source.getTextName(), Instant.now());
        if (!result.ignited()) {
            source.sendFailure(result.failure().message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.nether.portal.ignited",
                NetherSchedulePolicy.formatRemaining(Duration.between(Instant.now(), result.charge().expiresAt()))
        ), true);
        return 1;
    }

    private static int extinguishNetherPortal(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DimensionControlManager control = DimensionControlManager.get(source.getServer());
        if (control.netherPortalCharge().isEmpty() && control.netherPortal().isEmpty()) {
            source.sendFailure(Component.translatable("commands.kingdoms.nether.portal.missing"));
            return 0;
        }
        int moved = NetherPortalIgnition.extinguish(source.getServer(), "kingdoms.nether.portal.expired");
        source.sendSuccess(
                () -> Component.translatable("commands.kingdoms.nether.portal.extinguished", moved), true
        );
        return 1;
    }

    private static int netherPortalStatus(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> NetherPortalIgnition.statusMessage(context.getSource().getServer(), Instant.now()), false
        );
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
            DimensionControlEvents.broadcastOpened(source.getServer(), dimension);
            source.sendSuccess(() -> Component.literal(displayName + " открыт."), true);
            return 1;
        }
        int moved = DimensionControlEvents.evacuateForClosure(source.getServer(), dimension);
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
                    point.id().toString(),
                    point.dimension().location().toString(),
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
                () -> Component.translatable("kingdoms.trader.point.added", result.point().id().toString()), true
        );
        boolean spawned = TraderLifecycle.spawnContrabandNow(context.getSource().getServer(), result.point().id());
        context.getSource().sendSuccess(
                () -> Component.translatable(spawned
                        ? "kingdoms.trader.point.spawned"
                        : "kingdoms.trader.point.spawn_failed"),
                false
        );
        return 1;
    }

    private static int spawnContraband(CommandContext<CommandSourceStack> context) {
        if (!TraderLifecycle.spawnContrabandNow(context.getSource().getServer(), null)) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.point.spawn_failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("kingdoms.trader.point.spawned"), true);
        return 1;
    }

    private static int spawnWandering(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        UUID factionId = wanderingFactionId(context);
        if (factionId == null) {
            return 0;
        }
        if (!TraderLifecycle.spawnWanderingNow(context.getSource().getServer(), factionId)) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.wandering.spawn_failed"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("kingdoms.trader.wandering.spawn_forced"), true);
        return 1;
    }

    private static UUID wanderingFactionId(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        FactionManager manager = FactionManager.get(context.getSource().getServer());
        if (context.getNodes().stream().noneMatch(node -> "faction".equals(node.getNode().getName()))) {
            ServerPlayer player = context.getSource().getPlayerOrException();
            Faction faction = manager.getFactionForMember(player.getUUID()).orElse(null);
            if (faction == null) {
                context.getSource().sendFailure(Component.literal("Вы не состоите во фракции."));
                return null;
            }
            return faction.id();
        }
        String name = StringArgumentType.getString(context, "faction");
        Faction faction = manager.getFactionByName(name).orElse(null);
        if (faction == null) {
            context.getSource().sendFailure(Component.literal("Фракция не найдена: " + name));
            return null;
        }
        return faction.id();
    }

    private static int traderPointsRemove(CommandContext<CommandSourceStack> context) {
        UUID id;
        try {
            id = UUID.fromString(StringArgumentType.getString(context, "id"));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.point.invalid_id"));
            return 0;
        }
        if (!TraderLifecycle.removePoint(context.getSource().getServer(), id)) {
            context.getSource().sendFailure(Component.translatable("kingdoms.trader.point.not_found", id.toString()));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("kingdoms.trader.point.removed", id.toString()), true
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

    private static int setFactionBonuses(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        FactionManager manager = FactionManager.get(player.serverLevel());
        UUID factionId = manager.getFactionIdForMember(player.getUUID()).orElse(null);
        if (factionId == null) {
            source.sendFailure(Component.literal("Вы не состоите во фракции."));
            return 0;
        }
        com.geydev.kalfactions.faction.FactionBonus first = parseBonus(context, "first");
        com.geydev.kalfactions.faction.FactionBonus second = parseBonus(context, "second");
        if (first == null || second == null) {
            source.sendFailure(Component.literal("Неизвестный бонус."));
            return 0;
        }
        if (first == second) {
            source.sendFailure(Component.literal("Бонусы должны быть разными."));
            return 0;
        }
        manager.setFactionBonuses(factionId, java.util.Set.of(first, second));
        source.sendSuccess(
                () -> Component.literal("Бонусы фракции: " + first.name() + ", " + second.name()),
                true
        );
        return 1;
    }

    private static com.geydev.kalfactions.faction.FactionBonus parseBonus(
            CommandContext<CommandSourceStack> context,
            String argument
    ) {
        try {
            return com.geydev.kalfactions.faction.FactionBonus.parse(
                    StringArgumentType.getString(context, argument)
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
