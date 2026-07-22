package com.geydev.kalfactions.sculk;

import com.geydev.kalfactions.KalFactions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class SculkTomeEvents {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (SculkTomePolicy.isTome(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("kingdoms.sculk_tome.consumed_hint").withStyle(ChatFormatting.GOLD));
        } else if (SculkTomePolicy.requiresTome(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("kingdoms.sculk_gun.requires_tome").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private SculkTomeEvents() {
    }
}
