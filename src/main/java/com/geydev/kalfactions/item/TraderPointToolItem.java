package com.geydev.kalfactions.item;

import com.geydev.kalfactions.outpost.trader.TraderPointToolMode;
import com.geydev.kalfactions.outpost.trader.TraderWorldData;
import com.geydev.kalfactions.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class TraderPointToolItem extends Item {
    private static final double REMOVE_RADIUS = 2.5D;

    public TraderPointToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        if (!allowed(player)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            cycle(stack, player);
            return InteractionResult.SUCCESS;
        }
        TraderPointToolMode mode = stack.getOrDefault(ModDataComponents.TRADER_POINT_TOOL_MODE, TraderPointToolMode.EDIT);
        if (mode == TraderPointToolMode.SHOW) {
            show(level, player);
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TraderWorldData data = TraderWorldData.get(level.getServer());
        TraderWorldData.SpawnPoint nearest = data.nearestPoint(level.dimension(), pos, REMOVE_RADIUS).orElse(null);
        if (nearest != null) {
            data.removePoint(nearest.id());
            player.displayClientMessage(Component.translatable("kingdoms.trader.point.removed", nearest.id()), false);
            return InteractionResult.SUCCESS;
        }
        TraderWorldData.AddPointResult result = data.addPoint(level.dimension(), pos, player.getYRot());
        if (!result.added()) {
            player.displayClientMessage(Component.translatable("kingdoms.trader.point.limit"), false);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable("kingdoms.trader.point.added", result.point().id()), false);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (!allowed(serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown()) {
            cycle(stack, serverPlayer);
        } else {
            show(serverLevel, serverPlayer);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TraderPointToolMode mode = stack.getOrDefault(ModDataComponents.TRADER_POINT_TOOL_MODE, TraderPointToolMode.EDIT);
        tooltip.add(Component.translatable("item.kingdoms.trader_point_tool.mode", Component.translatable(
                "item.kingdoms.trader_point_tool.mode." + mode.id()
        )).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.kingdoms.trader_point_tool.tooltip").withStyle(ChatFormatting.GRAY));
    }

    private static boolean allowed(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("kingdoms.remover.no_permission"), true);
        return false;
    }

    private static void cycle(ItemStack stack, ServerPlayer player) {
        TraderPointToolMode mode = stack.getOrDefault(ModDataComponents.TRADER_POINT_TOOL_MODE, TraderPointToolMode.EDIT).next();
        stack.set(ModDataComponents.TRADER_POINT_TOOL_MODE, mode);
        player.displayClientMessage(Component.translatable(
                "item.kingdoms.trader_point_tool.mode",
                Component.translatable("item.kingdoms.trader_point_tool.mode." + mode.id())
        ), true);
    }

    private static void show(ServerLevel currentLevel, ServerPlayer player) {
        List<TraderWorldData.SpawnPoint> points = TraderWorldData.get(currentLevel.getServer()).points();
        player.sendSystemMessage(Component.translatable("kingdoms.trader.point.list.header", points.size()));
        int number = 0;
        for (TraderWorldData.SpawnPoint point : points) {
            number++;
            int displayNumber = number;
            player.sendSystemMessage(Component.translatable(
                    "kingdoms.trader.point.list.entry",
                    displayNumber,
                    point.id(),
                    point.dimension().location(),
                    point.pos().getX(),
                    point.pos().getY(),
                    point.pos().getZ(),
                    String.format(java.util.Locale.ROOT, "%.1f", point.yaw())
            ));
            ServerLevel level = currentLevel.getServer().getLevel(point.dimension());
            if (level == null || level != currentLevel || !level.isLoaded(point.pos())) {
                continue;
            }
            for (int offset = 0; offset < Math.min(displayNumber, 8); offset++) {
                level.sendParticles(
                        player,
                        ParticleTypes.ENCHANT,
                        true,
                        point.pos().getX() + 0.5D,
                        point.pos().getY() + 0.25D + offset * 0.2D,
                        point.pos().getZ() + 0.5D,
                        6,
                        0.15D,
                        0.05D,
                        0.15D,
                        0.0D
                );
            }
        }
    }
}
