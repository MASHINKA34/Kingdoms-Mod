package com.geydev.kalfactions.item;

import com.geydev.kalfactions.market.PlotSelection;
import com.geydev.kalfactions.registry.ModDataComponents;
import com.geydev.kalfactions.safezone.SafeZoneManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class SafeZoneWandItem extends Item {
    public SafeZoneWandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static PlotSelection selectionOf(ItemStack stack) {
        return stack.getItem() instanceof SafeZoneWandItem
                ? stack.get(ModDataComponents.SAFE_ZONE_SELECTION.get())
                : null;
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
                player.displayClientMessage(
                        Component.translatable("kingdoms.safezone.wand.no_permission"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (player.isShiftKeyDown()) {
            stack.remove(ModDataComponents.SAFE_ZONE_SELECTION.get());
            player.displayClientMessage(Component.translatable("kingdoms.safezone.wand.cleared"), true);
            return InteractionResult.CONSUME;
        }

        PlotSelection selection = stack.get(ModDataComponents.SAFE_ZONE_SELECTION.get());
        if (selection == null || selection.isComplete() || !selection.matchesDimension(level)) {
            stack.set(ModDataComponents.SAFE_ZONE_SELECTION.get(), PlotSelection.start(level, pos));
            player.displayClientMessage(Component.translatable(
                    "kingdoms.safezone.wand.first_corner", pos.getX(), pos.getY(), pos.getZ()), true);
            return InteractionResult.CONSUME;
        }
        PlotSelection completed = selection.withSecond(pos);
        stack.set(ModDataComponents.SAFE_ZONE_SELECTION.get(), completed);
        BoundingBox box = completed.box().orElseThrow();
        player.displayClientMessage(Component.translatable(
                "kingdoms.safezone.wand.size",
                box.getXSpan(),
                box.getYSpan(),
                box.getZSpan()
        ), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("kingdoms.safezone.wand.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("kingdoms.safezone.wand.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("kingdoms.safezone.wand.tooltip.resize")
                .withStyle(ChatFormatting.DARK_GRAY));
        PlotSelection selection = stack.get(ModDataComponents.SAFE_ZONE_SELECTION.get());
        if (selection == null) {
            return;
        }
        BoundingBox box = selection.box().orElse(null);
        if (box == null) {
            BlockPos first = selection.first();
            tooltip.add(Component.translatable(
                    "kingdoms.safezone.wand.tooltip.first",
                    first.getX(), first.getY(), first.getZ()
            ).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable(
                "kingdoms.safezone.wand.tooltip.selection",
                box.getXSpan(),
                box.getYSpan(),
                box.getZSpan(),
                SafeZoneManager.MAX_SIDE
        ).withStyle(ChatFormatting.GRAY));
    }
}
