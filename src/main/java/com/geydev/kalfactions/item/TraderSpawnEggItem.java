package com.geydev.kalfactions.item;

import com.geydev.kalfactions.outpost.trader.TraderService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class TraderSpawnEggItem extends Item {
    public TraderSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        Player player = context.getPlayer();
        float yRot = player == null ? 0.0F : player.getYRot();
        if (TraderService.spawn(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yRot)) {
            ItemStack stack = context.getItemInHand();
            if (player == null || !player.isCreative()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.CONSUME;
    }
}
