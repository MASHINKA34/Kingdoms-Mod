package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.net.ClaimSyncManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class DungeonCommands {
    private static final double PICK_DISTANCE = 6.0D;

    private static final SuggestionProvider<CommandSourceStack> DUNGEON_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    DungeonManager.get(context.getSource().getServer()).all().stream()
                            .map(dungeon -> StringArgumentType.escapeIfRequired(dungeon.name())),
                    builder
            );

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("dungeon")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(DungeonCommands::create)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(DUNGEON_SUGGESTIONS)
                                .executes(DungeonCommands::remove)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(DUNGEON_SUGGESTIONS)
                                .then(Commands.argument("newName", StringArgumentType.greedyString())
                                        .executes(DungeonCommands::rename))))
                .then(Commands.literal("list").executes(DungeonCommands::list))
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(DUNGEON_SUGGESTIONS)
                                .executes(DungeonCommands::teleport)))
                .then(Commands.literal("status").executes(DungeonCommands::status))
                .then(Commands.literal("loot")
                        .then(Commands.literal("mark")
                                .executes(context -> markLoot(context, null))
                                .then(Commands.argument("loot_table", ResourceLocationArgument.id())
                                        .executes(context -> markLoot(
                                                context,
                                                ResourceLocationArgument.getId(context, "loot_table")
                                        ))))
                        .then(Commands.literal("unmark").executes(DungeonCommands::unmarkLoot))
                        .then(Commands.literal("reset")
                                .then(Commands.literal("here").executes(DungeonCommands::resetLootHere))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(DUNGEON_SUGGESTIONS)
                                        .executes(DungeonCommands::resetLootDungeon)))
                        .then(Commands.literal("cooldown")
                                .then(Commands.argument("hours", IntegerArgumentType.integer(0, 8760))
                                        .executes(DungeonCommands::setCooldown))));
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String name = StringArgumentType.getString(context, "name");
        DungeonManager.CreateResult result =
                DungeonManager.get(level).create(level, player.blockPosition(), name);
        if (!result.successful()) {
            source.sendFailure(failure(result.reason()));
            return 0;
        }
        ClaimSyncManager.resyncAll(level.getServer());
        DungeonManager.DungeonView dungeon = result.dungeon();
        source.sendSuccess(() -> Component.literal(
                "Данж «" + dungeon.name() + "» создан (#" + dungeon.id() + "). "
                        + "Отметьте чанки на карте через краеугольный камень данжа."), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.DungeonView dungeon = find(context, manager);
        if (dungeon == null) {
            source.sendFailure(Component.literal("Данж не найден."));
            return 0;
        }
        manager.remove(dungeon.id());
        ClaimSyncManager.resyncAll(level.getServer());
        source.sendSuccess(() -> Component.literal("Данж «" + dungeon.name() + "» удалён."), true);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.DungeonView dungeon = manager
                .byName(StringArgumentType.getString(context, "name"))
                .orElse(null);
        if (dungeon == null) {
            source.sendFailure(Component.literal("Данж не найден."));
            return 0;
        }
        String newName = StringArgumentType.getString(context, "newName");
        DungeonManager.Reason reason = manager.rename(dungeon.id(), newName);
        if (reason != DungeonManager.Reason.OK) {
            source.sendFailure(failure(reason));
            return 0;
        }
        ClaimSyncManager.resyncAll(level.getServer());
        String applied = manager.byId(dungeon.id()).map(DungeonManager.DungeonView::name).orElse(newName);
        source.sendSuccess(() -> Component.literal(
                "Данж «" + dungeon.name() + "» переименован в «" + applied + "»."), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        List<DungeonManager.DungeonView> dungeons = DungeonManager.get(level).all();
        if (dungeons.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Данжей нет."), false);
            return 0;
        }
        for (DungeonManager.DungeonView dungeon : dungeons) {
            BlockPos core = dungeon.corePos();
            source.sendSuccess(() -> Component.literal(
                    "#" + dungeon.id() + " «" + dungeon.name() + "» — чанков: " + dungeon.chunks().size()
                            + ", контейнеров: " + dungeon.containerCount()
                            + ", кор [" + core.getX() + " " + core.getY() + " " + core.getZ() + "] "
                            + dungeon.dimension().location()), false);
        }
        return dungeons.size();
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DungeonManager manager = DungeonManager.get(player.serverLevel());
        DungeonManager.DungeonView dungeon = find(context, manager);
        if (dungeon == null) {
            source.sendFailure(Component.literal("Данж не найден."));
            return 0;
        }
        ServerLevel target = player.getServer().getLevel(dungeon.dimension());
        if (target == null) {
            source.sendFailure(Component.literal("Измерение данжа недоступно."));
            return 0;
        }
        BlockPos core = dungeon.corePos();
        player.teleportTo(
                target,
                core.getX() + 0.5D,
                core.getY() + 1.0D,
                core.getZ() + 0.5D,
                player.getYRot(),
                player.getXRot()
        );
        source.sendSuccess(() -> Component.literal("Телепорт к данжу «" + dungeon.name() + "»."), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        source.sendSuccess(() -> Component.literal(
                "Данжей: " + manager.count()
                        + ", кулдаун лута: " + ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.getAsInt() + " ч."), false);
        DungeonManager.DungeonView here = manager
                .dungeonAt(ClaimKey.of(level, player.blockPosition()))
                .orElse(null);
        if (here == null) {
            source.sendSuccess(() -> Component.literal("Текущий чанк не принадлежит данжу."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(
                "Здесь: «" + here.name() + "» (#" + here.id() + "), чанков: " + here.chunks().size()
                        + ", контейнеров: " + here.containerCount()), false);
        return 1;
    }

    private static int markLoot(CommandContext<CommandSourceStack> context, ResourceLocation requestedTable)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = lookingAt(player);
        if (pos == null) {
            source.sendFailure(Component.literal("Смотрите на контейнер."));
            return 0;
        }
        DungeonManager manager = DungeonManager.get(level);
        if (!manager.isDungeon(level, pos)) {
            source.sendFailure(Component.literal("Этот блок не в чанках данжа."));
            return 0;
        }
        RandomizableContainer container = DungeonLoot.containerAt(level, pos);
        if (container == null) {
            source.sendFailure(Component.literal("Этот контейнер не поддерживает лут-таблицы."));
            return 0;
        }
        ResourceLocation table = requestedTable != null
                ? requestedTable
                : DungeonLoot.pendingTable(container).orElse(null);
        if (table == null) {
            source.sendFailure(Component.literal(
                    "У контейнера нет лут-таблицы — укажите её аргументом команды."));
            return 0;
        }
        manager.markLoot(level, pos, table, DungeonClock.now());
        source.sendSuccess(() -> Component.literal(
                "Контейнер [" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "] зарегистрирован: "
                        + table + "."), true);
        return 1;
    }

    private static int unmarkLoot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = lookingAt(player);
        if (pos == null || !DungeonManager.get(player.serverLevel()).unmarkLoot(player.serverLevel(), pos)) {
            source.sendFailure(Component.literal("Смотрите на зарегистрированный контейнер данжа."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Контейнер снят с учёта."), true);
        return 1;
    }

    private static int resetLootHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = lookingAt(player);
        if (pos == null || !DungeonManager.get(level).touchLoot(level, pos, 0L)) {
            source.sendFailure(Component.literal("Смотрите на зарегистрированный контейнер данжа."));
            return 0;
        }
        DungeonLoot.refreshIfDue(level, pos);
        source.sendSuccess(() -> Component.literal("Кулдаун контейнера сброшен, лут перезаполнен."), true);
        return 1;
    }

    private static int resetLootDungeon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.DungeonView dungeon = find(context, manager);
        if (dungeon == null) {
            source.sendFailure(Component.literal("Данж не найден."));
            return 0;
        }
        int reset = manager.resetLoot(dungeon.id());
        source.sendSuccess(() -> Component.literal(
                "Сброшен кулдаун контейнеров: " + reset + " (данж «" + dungeon.name() + "»)."), true);
        return reset;
    }

    private static int setCooldown(CommandContext<CommandSourceStack> context) {
        int hours = IntegerArgumentType.getInteger(context, "hours");
        ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.set(hours);
        ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.save();
        context.getSource().sendSuccess(
                () -> Component.literal("Кулдаун лута данжей: " + hours + " ч."),
                true
        );
        return 1;
    }

    private static DungeonManager.DungeonView find(
            CommandContext<CommandSourceStack> context,
            DungeonManager manager
    ) {
        return manager.byName(StringArgumentType.getString(context, "name")).orElse(null);
    }

    private static BlockPos lookingAt(ServerPlayer player) {
        HitResult hit = player.pick(PICK_DISTANCE, 1.0F, false);
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos()
                : null;
    }

    private static Component failure(DungeonManager.Reason reason) {
        return switch (reason) {
            case NOT_BLACK -> Component.literal("Данж можно ставить только в чёрной зоне.");
            case NAME_TAKEN -> Component.literal("Данж с таким названием уже есть.");
            case NAME_EMPTY -> Component.literal("Название не может быть пустым.");
            case TOO_MANY -> Component.literal("Достигнут предел количества данжей.");
            case TOO_MANY_CHUNKS -> Component.literal("Достигнут предел чанков в данже.");
            case NOT_FOUND -> Component.literal("Данж не найден.");
            case SANCTUARY -> Component.literal("Чанк относится к спавну.");
            case CLAIMED -> Component.literal("Чанк занят фракцией.");
            case OTHER_DUNGEON -> Component.literal("Чанк занят другим данжем.");
            case OK -> Component.literal("Готово.");
        };
    }

    private DungeonCommands() {
    }
}
