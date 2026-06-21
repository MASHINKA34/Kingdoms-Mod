package com.geydev.kalfactions.raid;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class RaidCommands {
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("kingdoms")
                .then(Commands.literal("raid")
                    .then(raidSub("force", RaidManager::forceRaid, "Рейд принудительно запущен для "))
                    .then(raidSub("warn", RaidManager::forceWarning, "Предупреждение о рейде запущено для "))
                    .then(raidSub("outpost", RaidManager::forceOutpostRaid, "Рейд на форпост запущен для ")))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> raidSub(String name, RaidAction action, String successPrefix) {
        return Commands.literal(name)
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("faction", StringArgumentType.greedyString())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    FactionManager.get(context.getSource().getServer()).factions().stream()
                        .map(Faction::name),
                    builder
                ))
                .executes(context -> runRaid(context, action, successPrefix)));
    }

    private static int runRaid(CommandContext<CommandSourceStack> context, RaidAction action, String successPrefix) {
        MinecraftServer server = context.getSource().getServer();
        String factionName = StringArgumentType.getString(context, "faction").trim();
        Faction faction = FactionManager.get(server).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            context.getSource().sendFailure(Component.literal("Фракция не найдена."));
            return 0;
        }
        RaidManager.ForceOutcome outcome = action.run(RaidManager.get(server), server, faction.id());
        return switch (outcome.status()) {
            case STARTED -> {
                Raid raid = outcome.raid();
                String where = "";
                if (raid != null) {
                    BlockPos pos = raid.targetPos();
                    where = " у " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                }
                String location = where;
                context.getSource().sendSuccess(
                    () -> Component.literal(successPrefix + faction.name() + location + "."),
                    true
                );
                yield Command.SINGLE_SUCCESS;
            }
            case ALREADY_ACTIVE -> {
                context.getSource().sendFailure(Component.literal("У фракции уже есть активный рейд."));
                yield 0;
            }
            case NO_TARGET -> {
                context.getSource().sendFailure(Component.literal("У фракции нет доступной цели (территории или форпоста)."));
                yield 0;
            }
            case SPAWN_FAILED -> {
                context.getSource().sendFailure(Component.literal("Не удалось создать рейдеров."));
                yield 0;
            }
            case FACTION_NOT_FOUND -> {
                context.getSource().sendFailure(Component.literal("Фракция больше не существует."));
                yield 0;
            }
        };
    }

    @FunctionalInterface
    private interface RaidAction {
        RaidManager.ForceOutcome run(RaidManager manager, MinecraftServer server, java.util.UUID factionId);
    }

    private RaidCommands() {
    }
}
