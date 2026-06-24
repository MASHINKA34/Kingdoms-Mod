package com.geydev.kalfactions.block;

import com.geydev.kalfactions.net.FactionServerHooks;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FactionTableBlock extends BaseEntityBlock {
    public static final MapCodec<FactionTableBlock> CODEC = simpleCodec(FactionTableBlock::new);

    public FactionTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BlockShapes.TABLE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        return open(level, pos, player);
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
        if (player.isSecondaryUseActive()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        open(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()
                && placer != null
                && level.getBlockEntity(pos) instanceof FactionTableBlockEntity table) {
            table.setPlacedBy(placer.getUUID());
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactionTableBlockEntity(pos, state);
    }

    private static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            FactionServerHooks.openFor(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
