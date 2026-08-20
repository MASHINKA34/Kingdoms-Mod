package com.geydev.kalfactions.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SculkKeyForgeBlock extends KeyForgeBlock {
    public static final MapCodec<SculkKeyForgeBlock> CODEC = simpleCodec(SculkKeyForgeBlock::new);

    private static final VoxelShape NORTH_SHAPE = createNorthShape();
    private static final VoxelShape SOUTH_SHAPE = rotateShape(NORTH_SHAPE, Direction.SOUTH);
    private static final VoxelShape EAST_SHAPE = rotateShape(NORTH_SHAPE, Direction.EAST);
    private static final VoxelShape WEST_SHAPE = rotateShape(NORTH_SHAPE, Direction.WEST);

    public SculkKeyForgeBlock(BlockBehaviour.Properties properties) {
        super(KeyForgeType.SCULK, properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape shapeFor(Direction facing) {
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
}
