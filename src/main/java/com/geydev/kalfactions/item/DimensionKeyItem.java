package com.geydev.kalfactions.item;

import com.geydev.kalfactions.dimension.DimensionNetwork;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class DimensionKeyItem extends Item {
    public DimensionKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("kingdoms.dimension.key_no_permission"),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }
        DimensionNetwork.sendState(serverPlayer, Component.empty(), true);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.dimension_key.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
