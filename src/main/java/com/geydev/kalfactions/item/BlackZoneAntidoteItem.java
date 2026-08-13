package com.geydev.kalfactions.item;

import com.geydev.kalfactions.blackzone.BlackZonePenalties;
import com.geydev.kalfactions.blackzone.BlackZoneService;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class BlackZoneAntidoteItem extends Item {
    private static final int DRINK_TICKS = 32;

    public BlackZoneAntidoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && !hasToll(serverPlayer)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.kingdoms.blackzone_antidote.nothing"),
                    true
            );
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || !hasToll(player)) {
            return stack;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return stack;
        }
        BlackZoneService.reset(server, player);
        player.displayClientMessage(Component.translatable("item.kingdoms.blackzone_antidote.used"), true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.blackzone_antidote.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.kingdoms.blackzone_antidote.tooltip.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static boolean hasToll(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        return BlackZoneService.accumulatedMillis(server, player.getUUID()) > 0L
                || BlackZonePenalties.healthPenaltyOn(player) > 0;
    }
}
