package com.geydev.kalfactions.block;

import com.geydev.kalfactions.block.GuideBoardBlock.GuideBoardPart;
import net.minecraft.core.Direction;
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

    private static final VoxelShape[][] GUIDE_BOARD = makeGuideBoardShapes();

    public static VoxelShape guideBoard(Direction facing, GuideBoardPart part) {
        return GUIDE_BOARD[facing.get2DDataValue()][part.ordinal()];
    }

    private static VoxelShape[][] makeGuideBoardShapes() {
        VoxelShape[][] shapes = new VoxelShape[4][GuideBoardPart.values().length];
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (GuideBoardPart part : GuideBoardPart.values()) {
                shapes[facing.get2DDataValue()][part.ordinal()] = guideBoardShape(facing, part);
            }
        }
        return shapes;
    }

    private static VoxelShape guideBoardShape(Direction facing, GuideBoardPart part) {
        VoxelShape shape = rotatedBox(facing, 0.0D, 5.0D, 4.0D, 16.0D, 35.0D, 13.0D);
        if (part == GuideBoardPart.LEFT) {
            return Shapes.or(shape, rotatedBox(facing, 5.0D, 0.0D, 6.0D, 10.0D, 7.0D, 12.0D));
        }
        if (part == GuideBoardPart.RIGHT) {
            return Shapes.or(shape, rotatedBox(facing, 6.0D, 0.0D, 6.0D, 11.0D, 7.0D, 12.0D));
        }
        return shape;
    }

    private static VoxelShape rotatedBox(
            Direction facing,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        return switch (facing) {
            case SOUTH -> Block.box(16.0D - maxX, minY, 16.0D - maxZ, 16.0D - minX, maxY, 16.0D - minZ);
            case EAST -> Block.box(16.0D - maxZ, minY, minX, 16.0D - minZ, maxY, maxX);
            case WEST -> Block.box(minZ, minY, 16.0D - maxX, maxZ, maxY, 16.0D - minX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }

    private BlockShapes() {
    }
}
