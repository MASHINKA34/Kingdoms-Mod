package com.geydev.kalfactions.block;

import com.geydev.kalfactions.dimension.NetherPortalIgnition;
import com.geydev.kalfactions.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class NetherPortalAnchorBlock extends Block {
    public NetherPortalAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetherPortalIgnition.showStatus(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!stack.is(ModItems.NETHER_IGNITER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            NetherPortalIgnition.igniteWithItem(serverPlayer, pos, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
