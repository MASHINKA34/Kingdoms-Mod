package com.geydev.kalfactions.item;

import com.geydev.kalfactions.dimension.DimensionControlManager;
import com.geydev.kalfactions.dimension.DimensionControlEvents;
import com.geydev.kalfactions.dimension.NetherReturnIntegration;
import com.geydev.kalfactions.dimension.ReturnBinding;
import com.geydev.kalfactions.dimension.ReturnChannelEvents;
import java.time.Instant;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

public final class NetherReturnItem extends Item {
    private static final int CHANNEL_TICKS = 40;

    public NetherReturnItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        ReturnBinding binding = NetherReturnIntegration.binding(stack).orElse(null);
        if (binding == null
                || !binding.playerId().equals(player.getUUID())
                || !level.dimension().equals(Level.NETHER)
                || !DimensionControlManager.get(serverPlayer.serverLevel().getServer()).isValidReturn(binding, Instant.now())) {
            serverPlayer.displayClientMessage(Component.translatable("kingdoms.nether.return.invalid"), true);
            return InteractionResultHolder.fail(stack);
        }
        ReturnChannelEvents.begin(serverPlayer, binding);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ReturnBinding binding = NetherReturnIntegration.binding(stack).orElse(null);
            if (binding != null && ReturnChannelEvents.complete(player, binding)) {
                DimensionControlEvents.teleportToOverworldReturn(player, binding.returnPos());
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof ServerPlayer player) {
            ReturnChannelEvents.cancel(player);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return CHANNEL_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kingdoms.nether_return.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
