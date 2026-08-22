package com.geydev.kalfactions.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class NetherIgniterItem extends Item {
    public NetherIgniterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.nether_igniter.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.nether_igniter.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
