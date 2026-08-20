package com.geydev.kalfactions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class DungeonKeyPedestalBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DungeonKeyPedestalActivation> ACTIVATION =
            EnumProperty.create("activation", DungeonKeyPedestalActivation.class);
    public static final int ACTIVE_TICKS = 400;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.5D, 0.0D, 0.5D, 15.5D, 2.0D, 15.5D),
            Block.box(2.0D, 2.0D, 2.0D, 14.0D, 3.25D, 14.0D),
            Block.box(3.0D, 3.25D, 3.0D, 13.0D, 6.0D, 13.0D),
            Block.box(4.0D, 6.0D, 4.0D, 12.0D, 11.25D, 12.0D),
            Block.box(2.0D, 11.25D, 2.0D, 14.0D, 14.25D, 14.0D),
            Block.box(5.0D, 14.25D, 5.0D, 11.0D, 15.15D, 11.0D)
    );

    public DungeonKeyPedestalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVATION, DungeonKeyPedestalActivation.NONE));
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
        DungeonKeyPedestalActivation activation = DungeonKeyPedestalActivation.fromKey(stack);
        if (activation == DungeonKeyPedestalActivation.NONE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(ACTIVATION) != DungeonKeyPedestalActivation.NONE) {
            return ItemInteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            activate((ServerLevel) level, pos, state, activation, stack, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    private void activate(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            DungeonKeyPedestalActivation activation,
            ItemStack stack,
            Player player
    ) {
        level.setBlock(pos, state.setValue(ACTIVATION, activation), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, ACTIVE_TICKS);
        notifySignalNeighbors(level, pos);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        playActivationSound(level, pos, activation);
        spawnActivationParticles(level, pos, activation);
    }

    private static void playActivationSound(
            ServerLevel level,
            BlockPos pos,
            DungeonKeyPedestalActivation activation
    ) {
        if (activation == DungeonKeyPedestalActivation.GHOST) {
            level.playSound(
                    null,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.9D,
                    pos.getZ() + 0.5D,
                    SoundEvents.SOUL_ESCAPE,
                    SoundSource.BLOCKS,
                    0.8F,
                    0.9F
            );
            return;
        }
        level.playSound(
                null,
                pos,
                switch (activation) {
                    case SCULK -> SoundEvents.SCULK_CATALYST_BLOOM;
                    case INFERNAL -> SoundEvents.RESPAWN_ANCHOR_CHARGE;
                    case MOSSY -> SoundEvents.AMETHYST_BLOCK_RESONATE;
                    default -> SoundEvents.STONE_HIT;
                },
                SoundSource.BLOCKS,
                0.8F,
                1.0F
        );
    }

    private static void spawnActivationParticles(
            ServerLevel level,
            BlockPos pos,
            DungeonKeyPedestalActivation activation
    ) {
        level.sendParticles(
                switch (activation) {
                    case GHOST -> ParticleTypes.SOUL_FIRE_FLAME;
                    case SCULK -> ParticleTypes.SCULK_SOUL;
                    case INFERNAL -> ParticleTypes.FLAME;
                    case MOSSY -> ParticleTypes.HAPPY_VILLAGER;
                    default -> ParticleTypes.SMOKE;
                },
                pos.getX() + 0.5D,
                pos.getY() + 0.95D,
                pos.getZ() + 0.5D,
                8,
                0.28D,
                0.12D,
                0.28D,
                0.015D
        );
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVATION) == DungeonKeyPedestalActivation.NONE) {
            return;
        }
        level.setBlock(pos, state.setValue(ACTIVATION, DungeonKeyPedestalActivation.NONE), Block.UPDATE_ALL);
        notifySignalNeighbors(level, pos);
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
        return state.getValue(ACTIVATION) == DungeonKeyPedestalActivation.NONE ? 0 : 15;
    }

    private static void notifySignalNeighbors(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
        level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
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
        if (!state.is(newState.getBlock())
                && state.getValue(ACTIVATION) != DungeonKeyPedestalActivation.NONE) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVATION);
    }
}
