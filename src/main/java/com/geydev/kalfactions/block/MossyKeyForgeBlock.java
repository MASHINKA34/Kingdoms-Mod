package com.geydev.kalfactions.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MossyKeyForgeBlock extends KeyForgeBlock {
    public static final MapCodec<MossyKeyForgeBlock> CODEC = simpleCodec(MossyKeyForgeBlock::new);

    private static final VoxelShape NORTH_SHAPE = createNorthShape();
    private static final VoxelShape SOUTH_SHAPE = rotateShape(NORTH_SHAPE, Direction.SOUTH);
    private static final VoxelShape EAST_SHAPE = rotateShape(NORTH_SHAPE, Direction.EAST);
    private static final VoxelShape WEST_SHAPE = rotateShape(NORTH_SHAPE, Direction.WEST);

    public MossyKeyForgeBlock(BlockBehaviour.Properties properties) {
        super(KeyForgeType.MOSSY, properties);
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
        VoxelShape shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.25D, 16.0D);
        shape = join(shape, Block.box(0.5D, 2.25D, 0.5D, 15.5D, 8.35D, 15.0D));
        shape = join(shape, Block.box(1.5D, 8.1D, 2.5D, 5.0D, 9.25D, 6.0D));
        shape = join(shape, Block.box(6.25D, 8.1D, 2.5D, 9.75D, 9.25D, 6.0D));
        shape = join(shape, Block.box(11.0D, 8.1D, 2.5D, 14.5D, 9.25D, 6.0D));
        shape = join(shape, Block.box(5.8D, 8.1D, 10.2D, 10.2D, 11.15D, 13.8D));
        shape = join(shape, Block.box(0.8D, 8.5D, 13.3D, 3.4D, 14.0D, 15.9D));
        shape = join(shape, Block.box(3.2D, 13.0D, 13.3D, 9.2D, 16.0D, 15.9D));
        shape = join(shape, Block.box(9.7D, 12.0D, 13.3D, 15.2D, 14.2D, 15.9D));
        shape = join(shape, Block.box(12.5D, 8.5D, 13.3D, 15.2D, 13.0D, 15.9D));
        return shape;
    }
}
