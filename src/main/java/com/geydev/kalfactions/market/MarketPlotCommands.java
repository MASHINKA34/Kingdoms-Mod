package com.geydev.kalfactions.market;

import com.geydev.kalfactions.registry.ModDataComponents;
import com.geydev.kalfactions.registry.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
                .then(Commands.literal("list")
                        .executes(MarketPlotCommands::list));
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ItemStack wand = wandWithSelection(player);
        PlotSelection selection = wand.isEmpty() ? null : wand.get(ModDataComponents.PLOT_SELECTION);
        if (selection == null || !selection.isComplete()) {
            source.sendFailure(Component.literal("Сначала выделите оба угла жезлом участка."));
            return 0;
        }
        if (!selection.matchesDimension(level)) {
            source.sendFailure(Component.literal("Выделение сделано в другом измерении."));
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
        source.sendSuccess(() -> Component.literal(
                "Торговый участок #" + plot.id() + " создан: "
                        + box.getXSpan() + "x" + box.getYSpan() + "x" + box.getZSpan()
                        + ", цена " + price + "."), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        int id = IntegerArgumentType.getInteger(context, "id");
        if (!MarketPlotManager.get(level).remove(id)) {
            source.sendFailure(Component.literal("Участок #" + id + " не найден."));
            return 0;
        }
        MarketPlotService.syncAll(level.getServer());
        source.sendSuccess(() -> Component.literal("Участок #" + id + " удалён (постройка не тронута)."), true);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        int id = IntegerArgumentType.getInteger(context, "id");
        long price = LongArgumentType.getLong(context, "price");
        MarketPlot plot = MarketPlotManager.get(level).byId(id).orElse(null);
        if (plot == null) {
            source.sendFailure(Component.literal("Участок #" + id + " не найден."));
            return 0;
        }
        plot.setBasePrice(price);
        MarketPlotManager.get(level).markChanged();
        MarketPlotService.syncAll(level.getServer());
        source.sendSuccess(() -> Component.literal("Базовая цена участка #" + id + " теперь " + price + "."), true);
        return 1;
    }

    private static int reclaim(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        int id = IntegerArgumentType.getInteger(context, "id");
        MarketPlot plot = MarketPlotManager.get(level).byId(id).orElse(null);
        if (plot == null) {
            source.sendFailure(Component.literal("Участок #" + id + " не найден."));
            return 0;
        }
        MarketPlotService.release(level, plot);
        source.sendSuccess(() -> Component.literal(
                "Участок #" + id + " изъят: постройка сброшена, снова выставлен на продажу."), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getPlayerOrException().serverLevel();
        var plots = MarketPlotManager.get(level).all();
        if (plots.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Торговых участков нет."), false);
            return 0;
        }
        for (MarketPlot plot : plots) {
            BoundingBox box = plot.box();
            String status = switch (plot.state()) {
                case FOR_SALE -> "продаётся за " + plot.basePrice();
                case OWNED -> "владелец " + plot.ownerName();
                case RESALE -> "владелец " + plot.ownerName() + ", перепродажа за " + plot.resalePrice();
            };
            source.sendSuccess(() -> Component.literal(
                    "#" + plot.id() + " [" + box.minX() + " " + box.minY() + " " + box.minZ()
                            + " -> " + box.maxX() + " " + box.maxY() + " " + box.maxZ() + "] " + status), false);
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
