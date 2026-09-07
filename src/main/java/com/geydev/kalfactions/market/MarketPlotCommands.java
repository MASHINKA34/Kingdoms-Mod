package com.geydev.kalfactions.market;

import com.geydev.kalfactions.registry.ModDataComponents;
import com.geydev.kalfactions.registry.ModBlocks;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class MarketPlotCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("plot")
                .then(Commands.literal("create")
                        .then(Commands.argument("price", LongArgumentType.longArg(1L, MarketPlotService.MAX_PRICE))
                                .executes(MarketPlotCommands::create)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(MarketPlotCommands::remove)))
                .then(Commands.literal("setprice")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("price", LongArgumentType.longArg(1L, MarketPlotService.MAX_PRICE))
                                        .executes(MarketPlotCommands::setPrice))))
                .then(Commands.literal("reclaim")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(MarketPlotCommands::reclaim)))
                .then(Commands.literal("export")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(MarketPlotCommands::export)))
                .then(Commands.literal("list")
                        .executes(MarketPlotCommands::list));
    }

    private static int export(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ItemStack wand = wandWithSelection(player);
        PlotSelection selection = wand.isEmpty() ? null : wand.get(ModDataComponents.PLOT_SELECTION);
        if (selection == null || !selection.isComplete() || !selection.matchesDimension(level)) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.select_area_first"));
            return 0;
        }
        BoundingBox box = selection.box().orElseThrow();
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > 8_000_000L) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.export_too_large", 8_000_000));
            return 0;
        }
        int cores = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).is(ModBlocks.QUARRY_CORE.get()) && ++cores > 1) {
                break;
            }
        }
        if (cores != 1) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.export_needs_core"));
            return 0;
        }
        StructureTemplateManager manager = level.getStructureManager();
        ResourceLocation id = ResourceLocationArgument.getId(context, "id");
        StructureTemplate template = manager.getOrCreate(id);
        template.fillFromWorld(
                level,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new Vec3i(box.getXSpan(), box.getYSpan(), box.getZSpan()),
                false,
                null
        );
        template.setAuthor(player.getGameProfile().getName());
        if (!manager.save(id)) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.export_save_failed"));
            return 0;
        }
        String path = manager.createAndValidatePathToGeneratedStructure(id, ".nbt")
                .toAbsolutePath()
                .toString();
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.plot.export_saved",
                box.getXSpan(), box.getYSpan(), box.getZSpan(), path
        ), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ItemStack wand = wandWithSelection(player);
        PlotSelection selection = wand.isEmpty() ? null : wand.get(ModDataComponents.PLOT_SELECTION);
        if (selection == null || !selection.isComplete()) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.select_corners_first"));
            return 0;
        }
        if (!selection.matchesDimension(level)) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.selection_other_dimension"));
            return 0;
        }
        BoundingBox box = selection.box().orElseThrow();
        var error = MarketPlotService.validateBox(level, box);
        if (error.isPresent()) {
            source.sendFailure(error.get());
            return 0;
        }
        long price = LongArgumentType.getLong(context, "price");
        MarketPlot plot = MarketPlotService.create(level, box, price);
        wand.remove(ModDataComponents.PLOT_SELECTION);
        source.sendSuccess(() -> Component.translatable(
                "commands.kingdoms.plot.created",
                plot.id(), box.getXSpan(), box.getYSpan(), box.getZSpan(), price), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        int id = IntegerArgumentType.getInteger(context, "id");
        if (!MarketPlotManager.get(level).remove(id)) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.not_found", id));
            return 0;
        }
        MarketPlotService.syncAll(level.getServer());
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.plot.removed", id), true);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        int id = IntegerArgumentType.getInteger(context, "id");
        long price = LongArgumentType.getLong(context, "price");
        MarketPlot plot = MarketPlotManager.get(level).byId(id).orElse(null);
        if (plot == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.not_found", id));
            return 0;
        }
        plot.setBasePrice(price);
        MarketPlotManager.get(level).markChanged();
        MarketPlotService.syncAll(level.getServer());
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.plot.price_set", id, price), true);
        return 1;
    }

    private static int reclaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        int id = IntegerArgumentType.getInteger(context, "id");
        MarketPlot plot = MarketPlotManager.get(level).byId(id).orElse(null);
        if (plot == null) {
            source.sendFailure(Component.translatable("commands.kingdoms.plot.not_found", id));
            return 0;
        }
        MarketPlotService.release(level, plot);
        source.sendSuccess(() -> Component.translatable("commands.kingdoms.plot.reclaimed", id), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        var plots = MarketPlotManager.get(level).all();
        if (plots.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.kingdoms.plot.none"), false);
            return 0;
        }
        for (MarketPlot plot : plots) {
            BoundingBox box = plot.box();
            Component status = switch (plot.state()) {
                case FOR_SALE -> Component.translatable(
                        "commands.kingdoms.plot.status.for_sale", plot.basePrice());
                case OWNED -> Component.translatable(
                        "commands.kingdoms.plot.status.owned", plot.ownerName());
                case RESALE -> Component.translatable(
                        "commands.kingdoms.plot.status.resale", plot.ownerName(), plot.resalePrice());
            };
            source.sendSuccess(() -> Component.translatable(
                    "commands.kingdoms.plot.list_line",
                    plot.id(),
                    box.minX(), box.minY(), box.minZ(),
                    box.maxX(), box.maxY(), box.maxZ(),
                    status), false);
        }
        return plots.size();
    }

    private static ItemStack wandWithSelection(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.PLOT_WAND.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private MarketPlotCommands() {
    }
}
