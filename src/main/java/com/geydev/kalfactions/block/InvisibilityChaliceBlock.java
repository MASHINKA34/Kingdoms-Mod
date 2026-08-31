package com.geydev.kalfactions.block;

import com.geydev.kalfactions.invisibility.InvisibilityNetwork;
import com.geydev.kalfactions.invisibility.TrueInvisibility;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class InvisibilityChaliceBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(6.0D, 2.0D, 6.0D, 10.0D, 12.0D, 10.0D),
            Block.box(4.0D, 12.0D, 4.0D, 12.0D, 14.0D, 12.0D),
            Block.box(2.0D, 14.0D, 2.0D, 14.0D, 19.0D, 14.0D),
            Block.box(0.0D, 19.0D, 0.0D, 16.0D, 24.0D, 2.0D),
            Block.box(0.0D, 19.0D, 14.0D, 16.0D, 24.0D, 16.0D),
            Block.box(0.0D, 19.0D, 2.0D, 2.0D, 24.0D, 14.0D),
            Block.box(14.0D, 19.0D, 2.0D, 16.0D, 24.0D, 14.0D)
    );

    public InvisibilityChaliceBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InvisibilityChaliceBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (isOperatorEditing(player)) {
            openSettings(level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return drink(level, pos, player);
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
        if (isOperatorEditing(player)) {
            openSettings(level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static boolean isOperatorEditing(Player player) {
        return player.hasPermissions(2) && player.isSecondaryUseActive();
    }

    private static void openSettings(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            InvisibilityNetwork.openSettings(serverPlayer, pos);
        }
    }

    private static InteractionResult drink(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        if (!(level.getBlockEntity(pos) instanceof InvisibilityChaliceBlockEntity chalice)) {
            return InteractionResult.FAIL;
        }
        int seconds = chalice.durationSeconds();
        TrueInvisibility.grant(player, seconds);
        level.playSound(
                null,
                pos,
                SoundEvents.GENERIC_DRINK,
                SoundSource.BLOCKS,
                0.7F,
                1.2F
        );
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.WITCH,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.7D,
                    pos.getZ() + 0.5D,
                    12,
                    0.2D,
                    0.25D,
                    0.2D,
                    0.0D
            );
        }
        player.displayClientMessage(
                Component.translatable("message.kingdoms.invisibility_chalice.granted", seconds),
                true
        );
        return InteractionResult.SUCCESS;
    }
}
