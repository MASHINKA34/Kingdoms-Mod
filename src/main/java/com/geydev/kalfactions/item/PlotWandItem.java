package com.geydev.kalfactions.item;

import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.registry.ModDataComponents;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PlotWandItem extends Item {
    public PlotWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!player.hasPermissions(2)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("kingdoms.plot.wand.no_permission"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (player.isShiftKeyDown()) {
            stack.remove(ModDataComponents.PLOT_SELECTION);
            player.displayClientMessage(Component.translatable("kingdoms.plot.wand.cleared"), true);
            return InteractionResult.CONSUME;
        }

        PlotSelection selection = stack.get(ModDataComponents.PLOT_SELECTION);
        if (selection == null || selection.isComplete() || !selection.matchesDimension(level)) {
            stack.set(ModDataComponents.PLOT_SELECTION, PlotSelection.start(level, pos));
            player.displayClientMessage(Component.translatable(
                    "kingdoms.plot.wand.first_corner", pos.getX(), pos.getY(), pos.getZ()), true);
        } else {
            stack.set(ModDataComponents.PLOT_SELECTION, selection.withSecond(pos));
            player.displayClientMessage(Component.translatable(
                    "kingdoms.plot.wand.second_corner", pos.getX(), pos.getY(), pos.getZ()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("kingdoms.plot.wand.tooltip"));
    }
}
