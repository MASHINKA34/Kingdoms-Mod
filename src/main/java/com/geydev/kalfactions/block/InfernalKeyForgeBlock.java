package com.geydev.kalfactions.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class InfernalKeyForgeBlock extends KeyForgeBlock {
    public static final MapCodec<InfernalKeyForgeBlock> CODEC = simpleCodec(InfernalKeyForgeBlock::new);

    private static final VoxelShape NORTH_SHAPE = createNorthShape();
    private static final VoxelShape SOUTH_SHAPE = rotateShape(NORTH_SHAPE, Direction.SOUTH);
    private static final VoxelShape EAST_SHAPE = rotateShape(NORTH_SHAPE, Direction.EAST);
    private static final VoxelShape WEST_SHAPE = rotateShape(NORTH_SHAPE, Direction.WEST);

    public InfernalKeyForgeBlock(BlockBehaviour.Properties properties) {
        super(KeyForgeType.INFERNAL, properties);
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
        VoxelShape shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);
        shape = join(shape, Block.box(0.5D, 3.0D, 0.75D, 15.5D, 9.0D, 15.0D));
        shape = join(shape, Block.box(1.25D, 9.0D, 1.5D, 14.75D, 10.5D, 10.0D));
        shape = join(shape, Block.box(5.3D, 9.0D, 5.5D, 10.7D, 10.25D, 10.0D));
        shape = join(shape, Block.box(5.25D, 9.0D, 11.0D, 10.75D, 13.1D, 14.0D));
        shape = join(shape, Block.box(0.0D, 5.0D, 6.35D, 1.5D, 9.0D, 8.65D));
        shape = join(shape, Block.box(14.5D, 5.0D, 6.35D, 16.0D, 9.0D, 8.65D));
        shape = join(shape, Block.box(1.25D, 9.0D, 13.75D, 4.25D, 16.0D, 15.5D));
        shape = join(shape, Block.box(11.75D, 9.0D, 13.75D, 14.75D, 16.0D, 15.5D));
        shape = join(shape, Block.box(3.25D, 14.5D, 14.25D, 12.75D, 16.0D, 15.5D));
        return shape;
    }

}
