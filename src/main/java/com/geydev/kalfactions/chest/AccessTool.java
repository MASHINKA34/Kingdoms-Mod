package com.geydev.kalfactions.chest;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Item-side hook for the container access editor.
 *
 * <p>The protection event invokes this before vanilla opens the container. A
 * network/UI module can install a handler later without changing this item.</p>
 */
public class AccessTool extends Item {
    private static volatile UseHandler useHandler = AccessTool::defaultUse;

    public AccessTool(Properties properties) {
        super(properties);
    }

    public static void installUseHandler(UseHandler handler) {
        useHandler = Objects.requireNonNull(handler, "handler");
    }

    public static ToolResult handleProtectedUse(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            InteractionHand hand
    ) {
        return useHandler.use(player, level, pos, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        return switch (handleProtectedUse(player, level, context.getClickedPos(), context.getHand())) {
            case PASS -> InteractionResult.PASS;
            case CONSUME -> InteractionResult.SUCCESS;
            case DENY -> InteractionResult.FAIL;
        };
    }

    private static ToolResult defaultUse(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            InteractionHand hand
    ) {
        player.displayClientMessage(
                Component.literal("Container access editor is not connected yet."),
                true
        );
        return ToolResult.CONSUME;
    }

    public enum ToolResult {
        PASS,
        CONSUME,
        DENY
    }

    @FunctionalInterface
    public interface UseHandler {
        ToolResult use(ServerPlayer player, ServerLevel level, BlockPos pos, InteractionHand hand);
    }
}
