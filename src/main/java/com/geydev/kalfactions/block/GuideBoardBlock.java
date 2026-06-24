package com.geydev.kalfactions.block;

import com.geydev.kalfactions.net.FactionServerHooks;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GuideBoardBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<GuideBoardPart> PART = EnumProperty.create("part", GuideBoardPart.class);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public GuideBoardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, GuideBoardPart.CENTER)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isModelPart(state)
                ? new GuideBoardBlockEntity(pos, state)
                : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1) {
            return null;
        }
        for (GuideBoardPart part : GuideBoardPart.values()) {
            BlockPos partPos = partPos(pos, facing, part);
            if (!canPlacePart(context, partPos) || !canPlacePart(context, partPos.above())) {
                return null;
            }
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(PART, GuideBoardPart.CENTER)
                .setValue(HALF, DoubleBlockHalf.LOWER);
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
        if (!level.isClientSide() && isModelPart(state)) {
            Direction facing = state.getValue(FACING);
            for (GuideBoardPart part : GuideBoardPart.values()) {
                BlockPos partPos = partPos(pos, facing, part);
                BlockState lower = state.setValue(PART, part).setValue(HALF, DoubleBlockHalf.LOWER);
                BlockState upper = lower.setValue(HALF, DoubleBlockHalf.UPPER);
                if (part != GuideBoardPart.CENTER) {
                    level.setBlock(partPos, lower, UPDATE_ALL);
                }
                level.setBlock(partPos.above(), upper, UPDATE_ALL);
            }
        }
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BlockShapes.guideBoard(state.getValue(FACING));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !isModelPart(state)) {
            BlockPos center = centerPos(pos, state);
            BlockState centerState = level.getBlockState(center);
            if (isMatchingPart(centerState, state.getValue(FACING), GuideBoardPart.CENTER, DoubleBlockHalf.LOWER)) {
                level.destroyBlock(center, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            removeOtherParts(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        return open(level, centerPos(pos, state), player);
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
        open(level, centerPos(pos, state), player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
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
        builder.add(FACING, PART, HALF);
    }

    private static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            FactionServerHooks.openGuide(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean canPlacePart(BlockPlaceContext context, BlockPos pos) {
        Level level = context.getLevel();
        return level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos).canBeReplaced(context);
    }

    private static Direction leftDirection(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static BlockPos centerPos(BlockPos pos, BlockState state) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        Direction left = leftDirection(state.getValue(FACING));
        return switch (state.getValue(PART)) {
            case CENTER -> lowerPos;
            case LEFT -> lowerPos.relative(left.getOpposite());
            case RIGHT -> lowerPos.relative(left);
        };
    }

    private static BlockPos partPos(BlockPos center, Direction facing, GuideBoardPart part) {
        Direction left = leftDirection(facing);
        return switch (part) {
            case CENTER -> center;
            case LEFT -> center.relative(left);
            case RIGHT -> center.relative(left.getOpposite());
        };
    }

    private void removeOtherParts(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos center = centerPos(pos, state);
        for (GuideBoardPart part : GuideBoardPart.values()) {
            BlockPos partPos = partPos(center, facing, part);
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                BlockPos removePos = half == DoubleBlockHalf.UPPER ? partPos.above() : partPos;
                if (!removePos.equals(pos) && isMatchingPart(level.getBlockState(removePos), facing, part, half)) {
                    level.setBlock(removePos, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    private static boolean isModelPart(BlockState state) {
        return state.getValue(PART) == GuideBoardPart.CENTER && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    private boolean isMatchingPart(BlockState state, Direction facing, GuideBoardPart part, DoubleBlockHalf half) {
        return state.is(this)
                && state.getValue(FACING) == facing
                && state.getValue(PART) == part
                && state.getValue(HALF) == half;
    }

    public enum GuideBoardPart implements StringRepresentable {
        LEFT("left"),
        CENTER("center"),
        RIGHT("right");

        private final String name;

        GuideBoardPart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
