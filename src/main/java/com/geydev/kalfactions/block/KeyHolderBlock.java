package com.geydev.kalfactions.block;

import com.geydev.kalfactions.keyholder.KeyHolderNetwork;
import com.geydev.kalfactions.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class KeyHolderBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public KeyHolderBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, Boolean.FALSE));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeyHolderBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (player.hasPermissions(2) && player.isSecondaryUseActive()) {
            openSettings(level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (holdsBossKey(player)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.displayClientMessage(
                    Component.translatable("message.kingdoms.key_holder.key_required"),
                    true
            );
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
        if (player.hasPermissions(2) && player.isSecondaryUseActive()) {
            openSettings(level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!stack.is(ModItems.BOSS_KEY.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        if (!(level.getBlockEntity(pos) instanceof KeyHolderBlockEntity holder)) {
            return ItemInteractionResult.FAIL;
        }
        if (state.getValue(POWERED)) {
            if (holder.mode() != KeyHolderMode.TOGGLE) {
                return ItemInteractionResult.FAIL;
            }
            deactivate((ServerLevel) level, pos, state);
            return ItemInteractionResult.SUCCESS;
        }
        activate((ServerLevel) level, pos, state, holder, stack, player);
        return ItemInteractionResult.SUCCESS;
    }

    private static boolean holdsBossKey(Player player) {
        return player.getMainHandItem().is(ModItems.BOSS_KEY.get())
                || player.getOffhandItem().is(ModItems.BOSS_KEY.get());
    }

    private static void openSettings(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            KeyHolderNetwork.openSettings(serverPlayer, pos);
        }
    }

    private void activate(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            KeyHolderBlockEntity holder,
            ItemStack stack,
            Player player
    ) {
        if (holder.consumeKey() && !player.isCreative()) {
            stack.shrink(1);
        }
        level.setBlock(pos, state.setValue(POWERED, Boolean.TRUE), Block.UPDATE_ALL);
        if (holder.mode() == KeyHolderMode.PULSE) {
            level.scheduleTick(pos, this, holder.pulseTicks());
        }
        notifySignalNeighbors(level, pos);
        level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.7F, 1.2F);
        level.sendParticles(
                ParticleTypes.END_ROD,
                pos.getX() + 0.5D,
                pos.getY() + 0.9D,
                pos.getZ() + 0.5D,
                8,
                0.24D,
                0.12D,
                0.24D,
                0.01D
        );
    }

    private void deactivate(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(POWERED, Boolean.FALSE), Block.UPDATE_ALL);
        notifySignalNeighbors(level, pos);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.6F);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof KeyHolderBlockEntity holder
                && holder.mode() == KeyHolderMode.TOGGLE) {
            return;
        }
        deactivate(level, pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return signalStrength(state);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return signalStrength(state);
    }

    private static int signalStrength(BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    private static void notifySignalNeighbors(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        level.updateNeighborsAt(pos, block);
        level.updateNeighbourForOutputSignal(pos, block);
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && state.getValue(POWERED)) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
}
