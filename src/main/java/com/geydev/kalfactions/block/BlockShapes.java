package com.geydev.kalfactions.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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

    private static final GuideBox[] GUIDE_BOARD_BOXES = {
            new GuideBox(-13.0D, 8.0D, 7.0D, 29.0D, 28.0D, 11.0D),
            new GuideBox(-12.5D, 8.5D, 5.75D, 28.5D, 27.5D, 6.75D),
            new GuideBox(-11.5D, 27.5D, 4.5D, 27.5D, 32.0D, 12.0D),
            new GuideBox(-11.5D, 5.0D, 4.5D, 27.5D, 9.5D, 12.0D),
            new GuideBox(-16.0D, 5.0D, 4.5D, -11.5D, 32.0D, 12.0D),
            new GuideBox(27.5D, 5.0D, 4.5D, 32.0D, 32.0D, 12.0D),
            new GuideBox(-12.5D, 1.5D, 7.0D, -8.5D, 5.0D, 11.5D),
            new GuideBox(24.5D, 1.5D, 7.0D, 28.5D, 5.0D, 11.5D),
            new GuideBox(-13.5D, 0.0D, 6.5D, -7.5D, 1.5D, 12.0D),
            new GuideBox(23.5D, 0.0D, 6.5D, 29.5D, 1.5D, 12.0D),
            new GuideBox(-2.0D, 12.0D, 4.75D, 16.0D, 23.0D, 5.35D),
            new GuideBox(-10.0D, 17.0D, 4.75D, -2.5D, 24.5D, 5.25D),
            new GuideBox(17.0D, 11.0D, 4.75D, 26.0D, 18.5D, 5.25D),
            new GuideBox(-5.5D, 17.5D, 3.9D, -3.5D, 19.5D, 4.5D),
            new GuideBox(13.5D, 12.5D, 3.9D, 15.5D, 14.5D, 4.5D),
            new GuideBox(22.5D, 11.5D, 3.9D, 24.5D, 13.5D, 4.5D),
            new GuideBox(-9.5D, 9.0D, 11.25D, -7.5D, 27.0D, 11.75D),
            new GuideBox(23.5D, 9.0D, 11.25D, 25.5D, 27.0D, 11.75D),
            new GuideBox(-11.5D, 26.75D, 3.75D, 27.5D, 27.5D, 4.25D),
            new GuideBox(-11.5D, 9.5D, 3.75D, 27.5D, 10.25D, 4.25D),
            new GuideBox(-12.25D, 10.25D, 3.75D, -11.5D, 26.75D, 4.25D),
            new GuideBox(27.5D, 10.25D, 3.75D, 28.25D, 26.75D, 4.25D),
            new GuideBox(-9.5D, 23.0D, 4.0D, -8.5D, 24.0D, 4.5D),
            new GuideBox(-0.5D, 21.5D, 4.0D, 0.5D, 22.5D, 4.5D),
            new GuideBox(23.5D, 17.0D, 4.0D, 24.5D, 18.0D, 4.5D),
            new GuideBox(-13.5D, 28.0D, 3.75D, -8.5D, 29.25D, 4.25D),
            new GuideBox(-14.75D, 25.5D, 3.75D, -13.5D, 29.25D, 4.25D),
            new GuideBox(24.5D, 28.0D, 3.75D, 29.5D, 29.25D, 4.25D),
            new GuideBox(29.5D, 25.5D, 3.75D, 30.75D, 29.25D, 4.25D),
            new GuideBox(-13.5D, 7.25D, 3.75D, -8.5D, 8.5D, 4.25D),
            new GuideBox(-14.75D, 7.25D, 3.75D, -13.5D, 11.0D, 4.25D),
            new GuideBox(24.5D, 7.25D, 3.75D, 29.5D, 8.5D, 4.25D),
            new GuideBox(29.5D, 7.25D, 3.75D, 30.75D, 11.0D, 4.25D),
            new GuideBox(4.5D, 29.5D, 3.75D, 11.5D, 31.75D, 4.25D),
            new GuideBox(5.5D, 28.5D, 3.75D, 10.5D, 29.5D, 4.25D),
            new GuideBox(7.0D, 27.5D, 3.75D, 9.0D, 28.5D, 4.25D),
            new GuideBox(5.25D, 30.2D, 3.0D, 10.75D, 31.0D, 3.5D),
            new GuideBox(5.25D, 31.0D, 3.0D, 6.25D, 32.0D, 3.5D),
            new GuideBox(7.5D, 31.0D, 3.0D, 8.5D, 32.0D, 3.5D),
            new GuideBox(9.75D, 31.0D, 3.0D, 10.75D, 32.0D, 3.5D),
            new GuideBox(7.25D, 28.65D, 3.0D, 8.75D, 30.1D, 3.5D)
    };
    private static final VoxelShape[][][] GUIDE_BOARD = makeGuideBoardShapes();

    public static VoxelShape guideBoard(
            Direction facing,
            GuideBoardBlock.GuideBoardPart part,
            DoubleBlockHalf half
    ) {
        return GUIDE_BOARD[part.ordinal()][half == DoubleBlockHalf.UPPER ? 1 : 0][facing.get2DDataValue()];
    }

    private static VoxelShape[][][] makeGuideBoardShapes() {
        GuideBoardBlock.GuideBoardPart[] parts = GuideBoardBlock.GuideBoardPart.values();
        VoxelShape[][][] shapes = new VoxelShape[parts.length][2][4];
        for (GuideBoardBlock.GuideBoardPart part : parts) {
            double cellMinX = switch (part) {
                case LEFT -> -16.0D;
                case CENTER -> 0.0D;
                case RIGHT -> 16.0D;
            };
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                int halfIndex = half == DoubleBlockHalf.UPPER ? 1 : 0;
                double cellMinY = halfIndex * 16.0D;
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    VoxelShape shape = Shapes.empty();
                    for (GuideBox box : GUIDE_BOARD_BOXES) {
                        double minX = Math.max(box.minX(), cellMinX);
                        double minY = Math.max(box.minY(), cellMinY);
                        double maxX = Math.min(box.maxX(), cellMinX + 16.0D);
                        double maxY = Math.min(box.maxY(), cellMinY + 16.0D);
                        if (minX < maxX && minY < maxY) {
                            shape = Shapes.or(shape, rotatedBox(
                                    facing,
                                    minX - cellMinX,
                                    minY - cellMinY,
                                    box.minZ(),
                                    maxX - cellMinX,
                                    maxY - cellMinY,
                                    box.maxZ()
                            ));
                        }
                    }
                    shapes[part.ordinal()][halfIndex][facing.get2DDataValue()] = shape.optimize();
                }
            }
        }
        return shapes;
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

    private record GuideBox(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
    }

    private BlockShapes() {
    }
}
