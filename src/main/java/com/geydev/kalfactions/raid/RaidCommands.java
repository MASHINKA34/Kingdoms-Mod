package com.geydev.kalfactions.raid;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
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
                    .then(Commands.literal("force")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                FactionManager.get(context.getSource().getServer()).factions().stream()
                                    .map(Faction::name),
                                builder
                            ))
                            .executes(RaidCommands::forceRaid))))
        );
    }

    private static int forceRaid(CommandContext<CommandSourceStack> context) {
        String factionName = StringArgumentType.getString(context, "faction").trim();
        Faction faction = FactionManager.get(context.getSource().getServer())
            .getFactionByName(factionName)
            .orElse(null);
        if (faction == null) {
            context.getSource().sendFailure(Component.literal("Фракция не найдена."));
            return 0;
        }
        RaidManager.ForceOutcome outcome = RaidManager.get(context.getSource().getServer())
            .forceRaid(context.getSource().getServer(), faction.id());
        return switch (outcome.status()) {
            case STARTED -> {
                Raid raid = outcome.raid();
                context.getSource().sendSuccess(
                    () -> Component.literal(
                        "Рейд принудительно запущен для "
                            + faction.name()
                            + " у "
                            + raid.targetPos().getX()
                            + " "
                            + raid.targetPos().getY()
                            + " "
                            + raid.targetPos().getZ()
                            + "."
                    ),
                    true
                );
                yield Command.SINGLE_SUCCESS;
            }
            case ALREADY_ACTIVE -> {
                context.getSource().sendFailure(Component.literal("У фракции уже есть активный рейд."));
                yield 0;
            }
            case NO_TARGET -> {
                context.getSource().sendFailure(Component.literal("У фракции нет доступной MAIN-территории."));
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

    private RaidCommands() {
    }
}
