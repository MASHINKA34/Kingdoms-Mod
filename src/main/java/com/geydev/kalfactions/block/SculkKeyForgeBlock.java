package com.geydev.kalfactions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SculkKeyForgeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NORTH_SHAPE = createNorthShape();
    private static final VoxelShape SOUTH_SHAPE = rotate(NORTH_SHAPE, Direction.SOUTH);
    private static final VoxelShape EAST_SHAPE = rotate(NORTH_SHAPE, Direction.EAST);
    private static final VoxelShape WEST_SHAPE = rotate(NORTH_SHAPE, Direction.WEST);

    public SculkKeyForgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
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
        builder.add(FACING);
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    private static VoxelShape createNorthShape() {
        VoxelShape shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.75D, 16.0D);
        shape = join(shape, Block.box(0.2D, 2.5D, 0.45D, 15.8D, 7.5D, 14.6D));
        shape = join(shape, Block.box(0.65D, 7.25D, 0.75D, 15.35D, 8.0D, 14.5D));
        shape = join(shape, Block.box(1.75D, 7.25D, 2.7D, 4.65D, 8.0D, 5.5D));
        shape = join(shape, Block.box(6.55D, 7.25D, 2.7D, 9.45D, 8.0D, 5.5D));
        shape = join(shape, Block.box(11.35D, 7.25D, 2.7D, 14.25D, 8.0D, 5.5D));
        shape = join(shape, Block.box(6.75D, 7.25D, 6.55D, 9.25D, 10.25D, 9.05D));
        shape = join(shape, Block.box(5.5D, 7.25D, 10.0D, 10.5D, 9.55D, 13.75D));
        shape = join(shape, Block.box(0.9D, 7.0D, 12.9D, 5.5D, 14.5D, 15.5D));
        shape = join(shape, Block.box(10.5D, 7.0D, 12.9D, 15.1D, 14.5D, 15.5D));
        shape = join(shape, Block.box(5.0D, 13.75D, 12.9D, 11.0D, 16.0D, 15.5D));
        return shape;
    }

    private static VoxelShape join(VoxelShape first, VoxelShape second) {
        return Shapes.joinUnoptimized(first, second, BooleanOp.OR).optimize();
    }

    private static VoxelShape rotate(VoxelShape source, Direction facing) {
        VoxelShape[] result = {Shapes.empty()};
        source.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> result[0] = Shapes.or(
                result[0],
                switch (facing) {
                    case SOUTH -> Shapes.box(
                            1.0D - maxX, minY, 1.0D - maxZ,
                            1.0D - minX, maxY, 1.0D - minZ
                    );
                    case EAST -> Shapes.box(
                            1.0D - maxZ, minY, minX,
                            1.0D - minZ, maxY, maxX
                    );
                    case WEST -> Shapes.box(
                            minZ, minY, 1.0D - maxX,
                            maxZ, maxY, 1.0D - minX
                    );
                    default -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                }
        ));
        return result[0].optimize();
    }
}
