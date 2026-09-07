package com.geydev.kalfactions.dungeon;

import com.geydev.kalfactions.block.DungeonChestBlockEntity;
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

    private static final SuggestionProvider<CommandSourceStack> TEMPLATE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    ChestTemplateManager.get(context.getSource().getServer()).all().stream()
                            .map(template -> StringArgumentType.escapeIfRequired(template.name())),
                    builder
            );

    private static final SuggestionProvider<CommandSourceStack> TEMPLATE_FILE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    ChestTemplateStorage.list(context.getSource().getServer()).stream()
                            .map(StringArgumentType::escapeIfRequired),
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
                                        .executes(DungeonCommands::setCooldown))))
                .then(Commands.literal("template")
                        .then(Commands.literal("list").executes(DungeonCommands::templateList))
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(DungeonCommands::templateSave)))
                        .then(Commands.literal("apply")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(TEMPLATE_SUGGESTIONS)
                                        .executes(DungeonCommands::templateApply)))
                        .then(Commands.literal("rename")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .suggests(TEMPLATE_SUGGESTIONS)
                                        .then(Commands.argument("newName", StringArgumentType.greedyString())
                                                .executes(DungeonCommands::templateRename))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(TEMPLATE_SUGGESTIONS)
                                        .executes(DungeonCommands::templateDelete)))
                        .then(Commands.literal("export")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(TEMPLATE_SUGGESTIONS)
                                        .executes(DungeonCommands::templateExport)))
                        .then(Commands.literal("import")
                                .then(Commands.argument("file", StringArgumentType.greedyString())
                                        .suggests(TEMPLATE_FILE_SUGGESTIONS)
                                        .executes(DungeonCommands::templateImport))));
    }

    private static int templateList(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        ChestTemplateManager manager = ChestTemplateManager.get(level);
        List<ChestTemplate> templates = manager.all();
        if (templates.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.template.none"), false);
            return 0;
        }
        for (ChestTemplate template : templates) {
            Component interval = template.cooldownHours() < 0
                    ? Component.translatable("commands.kingdoms.dungeon.template.shared_cooldown")
                    : Component.translatable(
                            "commands.kingdoms.dungeon.template.hours", template.cooldownHours());
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.dungeon.template.line",
                    template.name(), template.filledSlots(), template.author(), interval), false);
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.template.total",
                templates.size(),
                manager.totalBytes(source.getServer().registryAccess())), false);
        return templates.size();
    }

    private static int templateSave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DungeonChestBlockEntity chest = chestNear(player);
        if (chest == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.look_at_chest"));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        ChestTemplateManager.SaveResult result = ChestTemplateService.store(
                player,
                ChestTemplate.capture(
                        java.util.UUID.randomUUID(),
                        name,
                        player.getGameProfile().getName(),
                        DungeonClock.now(),
                        chest
                ),
                true
        );
        if (!result.successful()) {
            source.sendFailure(ChestTemplateService.reasonMessage(result.reason()));
            return 0;
        }
        ChestTemplateService.syncOpenScreens(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.template.saved",
                result.template().name(), result.template().filledSlots()), true);
        return 1;
    }

    private static int templateApply(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DungeonChestBlockEntity chest = chestNear(player);
        if (chest == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.look_at_chest"));
            return 0;
        }
        ChestTemplate template = findTemplate(context, player.serverLevel());
        if (template == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.not_found"));
            return 0;
        }
        template.applyTo(chest, true);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.template.applied", template.name()), true);
        return 1;
    }

    private static int templateRename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        ChestTemplateManager manager = ChestTemplateManager.get(level);
        ChestTemplate template = manager
                .byName(StringArgumentType.getString(context, "name"))
                .orElse(null);
        if (template == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.not_found"));
            return 0;
        }
        String newName = StringArgumentType.getString(context, "newName");
        ChestTemplateManager.Reason reason = manager.rename(template.id(), newName);
        if (reason != ChestTemplateManager.Reason.OK) {
            source.sendFailure(ChestTemplateService.reasonMessage(reason));
            return 0;
        }
        ChestTemplateService.syncOpenScreens(source.getServer());
        String applied = manager.byId(template.id()).map(ChestTemplate::name).orElse(newName);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.template.renamed", template.name(), applied), true);
        return 1;
    }

    private static int templateDelete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        ChestTemplateManager manager = ChestTemplateManager.get(level);
        ChestTemplate template = findTemplate(context, level);
        if (template == null || !manager.delete(template.id())) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.not_found"));
            return 0;
        }
        ChestTemplateService.syncOpenScreens(source.getServer());
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.template.deleted", template.name()), true);
        return 1;
    }

    private static int templateExport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        ChestTemplate template = findTemplate(context, level);
        if (template == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.not_found"));
            return 0;
        }
        try {
            java.nio.file.Path written = ChestTemplateStorage.write(
                    source.getServer(),
                    template,
                    source.getServer().registryAccess()
            );
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.dungeon.template.exported",
                    template.name(), written.getFileName().toString()), true);
            return 1;
        } catch (java.io.IOException exception) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.write_failed"));
            return 0;
        }
    }

    private static int templateImport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String requested = StringArgumentType.getString(context, "file");
        java.nio.file.Path file = ChestTemplateStorage
                .resolve(source.getServer(), requested)
                .orElse(null);
        if (file == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.bad_file_name"));
            return 0;
        }
        ChestTemplate imported;
        try {
            imported = ChestTemplateStorage
                    .read(file, source.getServer().registryAccess())
                    .orElse(null);
        } catch (java.io.IOException exception) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.read_failed"));
            return 0;
        }
        if (imported == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.template.file_missing"));
            return 0;
        }
        ChestTemplateManager.SaveResult result =
                ChestTemplateService.store(player, imported.withId(java.util.UUID.randomUUID()), false);
        if (!result.successful()) {
            source.sendFailure(ChestTemplateService.reasonMessage(result.reason()));
            return 0;
        }
        ChestTemplateService.syncOpenScreens(source.getServer());
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.template.imported",
                result.template().name(), result.template().filledSlots()), true);
        return 1;
    }

    private static ChestTemplate findTemplate(CommandContext<CommandSourceStack> context, ServerLevel level) {
        return ChestTemplateManager.get(level)
                .byName(StringArgumentType.getString(context, "name"))
                .orElse(null);
    }

    private static DungeonChestBlockEntity chestNear(ServerPlayer player) {
        BlockPos looking = lookingAt(player);
        if (looking != null
                && player.serverLevel().getBlockEntity(looking) instanceof DungeonChestBlockEntity chest) {
            return chest;
        }
        BlockPos origin = player.blockPosition();
        DungeonChestBlockEntity closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -2, -2), origin.offset(2, 2, 2))) {
            if (!(player.serverLevel().getBlockEntity(pos) instanceof DungeonChestBlockEntity chest)) {
                continue;
            }
            double distance = player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = chest;
            }
        }
        return closest;
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
        DungeonManager.DungeonView dungeon = result.dungeon();
        DungeonManager.get(level).setClaims(
                level,
                dungeon.id(),
                List.of(ClaimKey.of(level, player.blockPosition())),
                true
        );
        ClaimSyncManager.resyncAll(level.getServer());
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.created", dungeon.name(), dungeon.id()), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.DungeonView dungeon = find(context, manager);
        if (dungeon == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.not_found"));
            return 0;
        }
        manager.remove(dungeon.id());
        ClaimSyncManager.resyncAll(level.getServer());
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.removed", dungeon.name()), true);
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
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.not_found"));
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
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.renamed", dungeon.name(), applied), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        List<DungeonManager.DungeonView> dungeons = DungeonManager.get(level).all();
        if (dungeons.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.list_empty"), false);
            return 0;
        }
        for (DungeonManager.DungeonView dungeon : dungeons) {
            BlockPos core = dungeon.corePos();
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.dungeon.list_line",
                    dungeon.id(), dungeon.name(), dungeon.chunks().size(), dungeon.containerCount(),
                    core.getX(), core.getY(), core.getZ(),
                    dungeon.dimension().location().toString()), false);
        }
        return dungeons.size();
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DungeonManager manager = DungeonManager.get(player.serverLevel());
        DungeonManager.DungeonView dungeon = find(context, manager);
        if (dungeon == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.not_found"));
            return 0;
        }
        ServerLevel target = player.getServer().getLevel(dungeon.dimension());
        if (target == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.dimension_missing"));
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
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.teleported", dungeon.name()), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.status",
                manager.count(), ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.getAsInt()), false);
        DungeonManager.DungeonView here = manager
                .dungeonAt(ClaimKey.of(level, player.blockPosition()))
                .orElse(null);
        if (here == null) {
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.status_outside"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.status_here",
                here.name(), here.id(), here.chunks().size(), here.containerCount()), false);
        return 1;
    }

    private static int markLoot(CommandContext<CommandSourceStack> context, ResourceLocation requestedTable)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = lookingAt(player);
        if (pos == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.look_at_container"));
            return 0;
        }
        DungeonManager manager = DungeonManager.get(level);
        if (!manager.isDungeon(level, pos)) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.outside_chunks"));
            return 0;
        }
        RandomizableContainer container = DungeonLoot.containerAt(level, pos);
        if (container == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.no_loot_support"));
            return 0;
        }
        ResourceLocation table = requestedTable != null
                ? requestedTable
                : DungeonLoot.pendingTable(container).orElse(null);
        if (table == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.no_loot_table"));
            return 0;
        }
        manager.markLoot(level, pos, table, DungeonClock.now());
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.loot_marked",
                pos.getX(), pos.getY(), pos.getZ(), table.toString()), true);
        return 1;
    }

    private static int unmarkLoot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        BlockPos pos = lookingAt(player);
        if (pos == null || !DungeonManager.get(player.serverLevel()).unmarkLoot(player.serverLevel(), pos)) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.look_at_marked"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.loot_unmarked"), true);
        return 1;
    }

    private static int resetLootHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = lookingAt(player);
        if (pos != null
                && level.getBlockEntity(pos) instanceof com.geydev.kalfactions.block.DungeonChestBlockEntity chest) {
            chest.resetCooldown();
            chest.refillIfDue();
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.chest_refilled"), true);
            return 1;
        }
        if (pos == null || !DungeonManager.get(level).touchLoot(level, pos, 0L)) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.look_at_marked"));
            return 0;
        }
        DungeonLoot.refreshIfDue(level, pos);
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.dungeon.loot_reset_one"), true);
        return 1;
    }

    private static int resetLootDungeon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        DungeonManager manager = DungeonManager.get(level);
        DungeonManager.DungeonView dungeon = find(context, manager);
        if (dungeon == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.dungeon.not_found"));
            return 0;
        }
        int reset = manager.resetLoot(dungeon.id());
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.dungeon.loot_reset_many", reset, dungeon.name()), true);
        return reset;
    }

    private static int setCooldown(CommandContext<CommandSourceStack> context) {
        int hours = IntegerArgumentType.getInteger(context, "hours");
        ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.set(hours);
        ModConfigSpec.DUNGEON_LOOT_COOLDOWN_HOURS.save();
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.kingdoms.dungeon.cooldown_set", hours),
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
            case NOT_BLACK -> Component.translatable("commands.kingdoms.dungeon.reason.not_black");
            case NAME_TAKEN -> Component.translatable("commands.kingdoms.dungeon.reason.name_taken");
            case NAME_EMPTY -> Component.translatable("commands.kingdoms.dungeon.reason.name_empty");
            case TOO_MANY -> Component.translatable("commands.kingdoms.dungeon.reason.too_many");
            case TOO_MANY_CHUNKS -> Component.translatable("commands.kingdoms.dungeon.reason.too_many_chunks");
            case NOT_FOUND -> Component.translatable("commands.kingdoms.dungeon.not_found");
            case SANCTUARY -> Component.translatable("commands.kingdoms.dungeon.reason.sanctuary");
            case CLAIMED -> Component.translatable("commands.kingdoms.dungeon.reason.claimed");
            case OTHER_DUNGEON -> Component.translatable("commands.kingdoms.dungeon.reason.other_dungeon");
            case OK -> Component.translatable("commands.kingdoms.dungeon.reason.ok");
        };
    }

    private DungeonCommands() {
    }
}
