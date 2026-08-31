package com.geydev.kalfactions.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class BossKeyItem extends Item {
    public BossKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.boss_key.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.boss_key.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
