package com.geydev.kalfactions.faith;

import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class FaithCommands {
    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    FactionManager.get(context.getSource().getServer()).factions().stream().map(Faction::name),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> GODS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(FaithGod.VALUES).map(FaithGod::id),
                    builder
            );

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("faith")
                .then(Commands.literal("status")
                        .executes(context -> status(context, null))
                        .then(Commands.argument("faction", StringArgumentType.greedyString())
                                .suggests(FACTIONS)
                                .executes(context ->
                                        status(context, StringArgumentType.getString(context, "faction")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("faction", StringArgumentType.string())
                                .suggests(FACTIONS)
                                .then(Commands.argument("god", StringArgumentType.word())
                                        .suggests(GODS)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(
                                                        FaithGod.MIN_LEVEL, FaithGod.MAX_LEVEL))
                                                .executes(FaithCommands::setLevel)))))
                .then(Commands.literal("buff")
                        .then(Commands.argument("faction", StringArgumentType.string())
                                .suggests(FACTIONS)
                                .then(Commands.argument("god", StringArgumentType.word())
                                        .suggests(GODS)
                                        .executes(FaithCommands::grantBuff))))
                .then(Commands.literal("quest")
                        .then(Commands.literal("reroll")
                                .then(Commands.argument("faction", StringArgumentType.string())
                                        .suggests(FACTIONS)
                                        .then(Commands.argument("god", StringArgumentType.word())
                                                .suggests(GODS)
                                                .executes(FaithCommands::rerollQuest)))));
    }

    private static int status(CommandContext<CommandSourceStack> context, String factionName) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        FaithManager manager = FaithManager.get(server);
        java.util.List<Faction> targets = new java.util.ArrayList<>();
        if (factionName == null) {
            targets.addAll(FactionManager.get(server).factions());
        } else {
            Faction faction = FactionManager.get(server).getFactionByName(factionName).orElse(null);
            if (faction == null) {
                source.sendFailure(Component.literal("Фракция не найдена: " + factionName));
                return 0;
            }
            targets.add(faction);
        }
        if (targets.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Фракций нет."), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        for (Faction faction : targets) {
            source.sendSuccess(() -> Component.literal(faction.name() + ":"), false);
            for (FaithGod god : FaithGod.VALUES) {
                int level = manager.level(faction.id(), god);
                long remaining = Math.max(0L, manager.buffEndMillis(faction.id(), god) - now);
                String buff = remaining > 0L
                        ? String.format(Locale.ROOT, "баф %d:%02d", remaining / 60_000L, remaining / 1000L % 60L)
                        : "бафа нет";
                source.sendSuccess(() -> Component.literal(
                        "  " + god.id() + ": уровень " + level + "/" + FaithGod.MAX_LEVEL + ", " + buff), false);
            }
        }
        return targets.size();
    }

    private static int setLevel(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Faction faction = faction(context);
        FaithGod god = god(context);
        if (faction == null || god == null) {
            return 0;
        }
        int level = IntegerArgumentType.getInteger(context, "level");
        FaithManager manager = FaithManager.get(source.getServer());
        manager.setLevel(faction.id(), god, level);
        FaithService.refreshMembers(source.getServer(), faction);
        source.sendSuccess(() -> Component.literal(
                "Вера " + god.id() + " у " + faction.name() + " теперь " + level + "."), true);
        return 1;
    }

    private static int grantBuff(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Faction faction = faction(context);
        FaithGod god = god(context);
        if (faction == null || god == null) {
            return 0;
        }
        int minutes = Math.max(1, ModConfigSpec.FAITH_BUFF_DURATION_MINUTES.getAsInt());
        FaithManager.get(source.getServer())
                .activateBuff(faction.id(), god, System.currentTimeMillis() + minutes * 60_000L);
        FaithService.refreshMembers(source.getServer(), faction);
        FaithService.notifyFaction(
                source.getServer(),
                faction,
                Component.translatable(
                        "kingdoms.faith.notice.buff_started",
                        Component.translatable(god.translationKey()),
                        minutes
                ),
                true
        );
        source.sendSuccess(() -> Component.literal(
                "Баф " + god.id() + " выдан фракции " + faction.name() + " на " + minutes + " мин."), true);
        return 1;
    }

    private static int rerollQuest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Faction faction = faction(context);
        FaithGod god = god(context);
        if (faction == null || god == null) {
            return 0;
        }
        int nonce = FaithManager.get(source.getServer()).reroll(faction.id(), god);
        source.sendSuccess(() -> Component.literal(
                "Квест " + god.id() + " у " + faction.name() + " перекатан (попытка " + nonce + ")."), true);
        return 1;
    }

    private static Faction faction(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "faction");
        Faction faction = FactionManager.get(context.getSource().getServer())
                .getFactionByName(name)
                .orElse(null);
        if (faction == null) {
            context.getSource().sendFailure(Component.literal("Фракция не найдена: " + name));
        }
        return faction;
    }

    private static FaithGod god(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "god");
        FaithGod god = FaithGod.parse(id).orElse(null);
        if (god == null) {
            context.getSource().sendFailure(Component.literal("Бог не найден: " + id));
        }
        return god;
    }

    private FaithCommands() {
    }
}
