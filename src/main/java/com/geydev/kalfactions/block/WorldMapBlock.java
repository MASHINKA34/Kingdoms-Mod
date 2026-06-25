package com.geydev.kalfactions.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

public final class WorldMapBlock extends Block implements EntityBlock {
    public static final int WIDTH = 5;
    public static final int HEIGHT = 5;
    public static final int HALF_WIDTH = WIDTH / 2;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, WIDTH * HEIGHT - 1);
    public static final int CONTROLLER_CELL = cellIndex(0, 0);

    public WorldMapBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CELL, CONTROLLER_CELL));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new WorldMapBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos controller = context.getClickedPos();
        Level level = context.getLevel();
        if (controller.getY() + HEIGHT - 1 >= level.getMaxBuildHeight()) {
            return null;
        }
        for (int row = 0; row < HEIGHT; row++) {
            for (int col = -HALF_WIDTH; col <= HALF_WIDTH; col++) {
                if (!canPlace(context, cellPos(controller, facing, col, row))) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(CELL, CONTROLLER_CELL);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !isController(state)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int row = 0; row < HEIGHT; row++) {
            for (int col = -HALF_WIDTH; col <= HALF_WIDTH; col++) {
                if (col == 0 && row == 0) {
                    continue;
                }
                level.setBlock(cellPos(pos, facing, col, row), state.setValue(CELL, cellIndex(col, row)), UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !isController(state)) {
            BlockPos controller = controllerPos(pos, state);
            BlockState controllerState = level.getBlockState(controller);
            if (controllerState.is(this) && isController(controllerState)
                    && controllerState.getValue(FACING) == state.getValue(FACING)) {
                level.destroyBlock(controller, !player.isCreative(), player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide()) {
            removeOtherCells(level, pos, state);
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
        builder.add(FACING, CELL);
    }

    public static boolean isController(BlockState state) {
        return state.hasProperty(CELL) && state.getValue(CELL) == CONTROLLER_CELL;
    }

    public static BlockPos controllerPos(BlockPos cellPos, BlockState state) {
        int cell = state.getValue(CELL);
        int col = (cell % WIDTH) - HALF_WIDTH;
        int row = cell / WIDTH;
        Direction left = state.getValue(FACING).getCounterClockWise();
        return cellPos.relative(left, -col).below(row);
    }

    private void removeOtherCells(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos controller = controllerPos(pos, state);
        for (int row = 0; row < HEIGHT; row++) {
            for (int col = -HALF_WIDTH; col <= HALF_WIDTH; col++) {
                BlockPos cell = cellPos(controller, facing, col, row);
                if (cell.equals(pos)) {
                    continue;
                }
                BlockState cellState = level.getBlockState(cell);
                if (cellState.is(this) && cellState.getValue(FACING) == facing
                        && cellState.getValue(CELL) == cellIndex(col, row)) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), UPDATE_ALL | UPDATE_SUPPRESS_DROPS);
                }
            }
        }
    }

    private static boolean canPlace(BlockPlaceContext context, BlockPos pos) {
        Level level = context.getLevel();
        return level.getWorldBorder().isWithinBounds(pos) && level.getBlockState(pos).canBeReplaced(context);
    }

    private static BlockPos cellPos(BlockPos controller, Direction facing, int col, int row) {
        return controller.relative(facing.getCounterClockWise(), col).above(row);
    }

    private static int cellIndex(int col, int row) {
        return row * WIDTH + (col + HALF_WIDTH);
    }
}
