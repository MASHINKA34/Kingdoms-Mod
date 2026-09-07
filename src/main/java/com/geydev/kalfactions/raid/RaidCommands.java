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
                    .then(raidSub("force", RaidManager::forceRaid, "commands.kingdoms.raid.forced"))
                    .then(raidSub("warn", RaidManager::forceWarning, "commands.kingdoms.raid.warned"))
                    .then(raidSub("outpost", RaidManager::forceOutpostRaid, "commands.kingdoms.raid.outpost_forced")))
        );
    }

    private static ArgumentBuilder<CommandSourceStack, ?> raidSub(String name, RaidAction action, String successKey) {
        return Commands.literal(name)
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("faction", StringArgumentType.greedyString())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    FactionManager.get(context.getSource().getServer()).factions().stream()
                        .map(Faction::name),
                    builder
                ))
                .executes(context -> runRaid(context, action, successKey)));
    }

    private static int runRaid(CommandContext<CommandSourceStack> context, RaidAction action, String successKey) {
        MinecraftServer server = context.getSource().getServer();
        String factionName = StringArgumentType.getString(context, "faction").trim();
        Faction faction = FactionManager.get(server).getFactionByName(factionName).orElse(null);
        if (faction == null) {
            context.getSource().sendFailure(Component.translatable("commands.kingdoms.raid.faction_not_found"));
            return 0;
        }
        RaidManager.ForceOutcome outcome = action.run(RaidManager.get(server), server, faction.id());
        return switch (outcome.status()) {
            case STARTED -> {
                Raid raid = outcome.raid();
                Component where = Component.empty();
                if (raid != null) {
                    BlockPos pos = raid.targetPos();
                    where = Component.translatable(
                        "commands.kingdoms.raid.at", pos.getX(), pos.getY(), pos.getZ());
                }
                Component location = where;
                context.getSource().sendSuccess(
                    () -> Component.translatable(successKey, faction.name(), location),
                    true
                );
                yield Command.SINGLE_SUCCESS;
            }
            case ALREADY_ACTIVE -> {
                context.getSource().sendFailure(Component.translatable("commands.kingdoms.raid.already_active"));
                yield 0;
            }
            case NO_TARGET -> {
                context.getSource().sendFailure(Component.translatable("commands.kingdoms.raid.no_target"));
                yield 0;
            }
            case SPAWN_FAILED -> {
                context.getSource().sendFailure(Component.translatable("commands.kingdoms.raid.spawn_failed"));
                yield 0;
            }
            case FACTION_NOT_FOUND -> {
                context.getSource().sendFailure(Component.translatable("commands.kingdoms.raid.faction_gone"));
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
