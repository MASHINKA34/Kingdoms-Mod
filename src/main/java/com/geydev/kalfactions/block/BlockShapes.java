package com.geydev.kalfactions.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockShapes {
    public static final VoxelShape TABLE = Shapes.or(
            Block.box(0.0D, 12.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(1.0D, 0.0D, 1.0D, 4.0D, 12.0D, 4.0D),
            Block.box(12.0D, 0.0D, 1.0D, 15.0D, 12.0D, 4.0D),
            Block.box(1.0D, 0.0D, 12.0D, 4.0D, 12.0D, 15.0D),
            Block.box(12.0D, 0.0D, 12.0D, 15.0D, 12.0D, 15.0D)
    );

    public static final VoxelShape GUIDE_BOARD = Shapes.or(
            Block.box(0.0D, 4.0D, 5.0D, 16.0D, 16.0D, 11.0D),
            Block.box(0.0D, 0.0D, 6.0D, 4.0D, 5.0D, 10.0D),
            Block.box(12.0D, 0.0D, 6.0D, 16.0D, 5.0D, 10.0D)
    );

    private BlockShapes() {
    }
}
