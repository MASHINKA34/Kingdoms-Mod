package com.geydev.kalfactions.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class BossTrophyItem extends Item {
    private final String tooltipKey;

    public BossTrophyItem(Properties properties, String tooltipKey) {
        super(properties);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.boss_trophy.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
