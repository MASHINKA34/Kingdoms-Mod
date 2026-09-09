package com.geydev.kalfactions.item;

import com.geydev.kalfactions.charon.CharonService;
import com.geydev.kalfactions.charon.CharonText;
import com.geydev.kalfactions.config.ModConfigSpec;
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
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class CharonTokenItem extends Item {
    private static final int DEFAULT_DEATH_DELAY_SECONDS = 180;
    private static final int DEFAULT_GHOST_SECONDS = 25;
    private static final int DEFAULT_GHOST_HEALTH = 5;
    private static final int DEFAULT_COOLDOWN_MINUTES = 30;

    public CharonTokenItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return CharonService.use(serverPlayer, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.charon_token.tooltip.usage").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "item.kingdoms.charon_token.tooltip.delay",
                CharonText.duration(configValue(ModConfigSpec.CHARON_DEATH_DELAY_SECONDS, DEFAULT_DEATH_DELAY_SECONDS))
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "item.kingdoms.charon_token.tooltip.duration",
                CharonText.duration(configValue(ModConfigSpec.CHARON_GHOST_SECONDS, DEFAULT_GHOST_SECONDS)),
                configValue(ModConfigSpec.CHARON_GHOST_HEALTH, DEFAULT_GHOST_HEALTH)
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                "item.kingdoms.charon_token.tooltip.cooldown",
                CharonText.duration(configValue(ModConfigSpec.CHARON_COOLDOWN_MINUTES, DEFAULT_COOLDOWN_MINUTES) * 60L)
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.charon_token.tooltip.rules").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static int configValue(IntValue value, int fallback) {
        try {
            return value.getAsInt();
        } catch (IllegalStateException configNotLoaded) {
            return fallback;
        }
    }
}
