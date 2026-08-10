package com.geydev.kalfactions.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StoneGodStatueBlock extends Block implements EntityBlock {
    public static final int HEIGHT = 8;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, HEIGHT - 1);

    private final VoxelShape northShape;
    private final VoxelShape eastShape;

    public StoneGodStatueBlock(
            BlockBehaviour.Properties properties,
            VoxelShape northShape,
            VoxelShape eastShape
    ) {
        super(properties);
        this.northShape = northShape;
        this.eastShape = eastShape;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SEGMENT, 0));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SEGMENT) == 0 ? new StoneGodStatueBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos base = context.getClickedPos();
        Level level = context.getLevel();
        if (base.getY() > level.getMaxBuildHeight() - HEIGHT) {
            return null;
        }
        for (int segment = 0; segment < HEIGHT; segment++) {
            if (!level.getBlockState(base.above(segment)).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(SEGMENT, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || state.getValue(SEGMENT) != 0) {
            return;
        }
        for (int segment = 1; segment < HEIGHT; segment++) {
            level.setBlock(pos.above(segment), state.setValue(SEGMENT, segment), UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? eastShape : northShape;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return getShape(state, level, pos, context);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && state.getValue(SEGMENT) != 0) {
            BlockPos base = pos.below(state.getValue(SEGMENT));
            BlockState baseState = level.getBlockState(base);
            if (isMatchingSegment(baseState, state.getValue(FACING), 0)) {
                level.destroyBlock(base, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            int removedSegment = state.getValue(SEGMENT);
            BlockPos base = pos.below(removedSegment);
            Direction facing = state.getValue(FACING);
            for (int segment = 0; segment < HEIGHT; segment++) {
                BlockPos segmentPos = base.above(segment);
                if (!segmentPos.equals(pos)
                        && isMatchingSegment(level.getBlockState(segmentPos), facing, segment)) {
                    level.setBlock(segmentPos, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
        builder.add(FACING, SEGMENT);
    }

    private boolean isMatchingSegment(BlockState state, Direction facing, int segment) {
        return state.is(this)
                && state.getValue(FACING) == facing
                && state.getValue(SEGMENT) == segment;
    }
}
