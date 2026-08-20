package com.geydev.kalfactions.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class GhostKeyForgeBlock extends KeyForgeBlock {
    public static final MapCodec<GhostKeyForgeBlock> CODEC = simpleCodec(GhostKeyForgeBlock::new);

    private static final VoxelShape NORTH_SHAPE = createNorthShape();
    private static final VoxelShape SOUTH_SHAPE = rotateShape(NORTH_SHAPE, Direction.SOUTH);
    private static final VoxelShape EAST_SHAPE = rotateShape(NORTH_SHAPE, Direction.EAST);
    private static final VoxelShape WEST_SHAPE = rotateShape(NORTH_SHAPE, Direction.WEST);

    public GhostKeyForgeBlock(BlockBehaviour.Properties properties) {
        super(KeyForgeType.GHOST, properties);
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
        VoxelShape shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D);
        shape = join(shape, Block.box(1.5D, 2.0D, 2.0D, 14.5D, 8.5D, 14.5D));
        shape = join(shape, Block.box(5.25D, 8.5D, 9.25D, 10.75D, 11.65D, 14.0D));
        shape = join(shape, Block.box(0.25D, 7.5D, 12.5D, 4.75D, 16.0D, 16.0D));
        shape = join(shape, Block.box(11.25D, 7.5D, 12.5D, 15.75D, 16.0D, 16.0D));
        shape = join(shape, Block.box(4.0D, 14.0D, 13.25D, 12.0D, 16.0D, 15.25D));
        return shape;
    }
}
