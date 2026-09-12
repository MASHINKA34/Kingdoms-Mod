package com.geydev.kalfactions.block;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Local collision cells sampled from the rotated source cuboids at 1/32 block resolution. */
final class CharonStatueShapes {
    private static final VoxelShape[][][][] SHAPES = load();

    static VoxelShape get(Direction facing, int x, int layer, int z) {
        return SHAPES[facing.get2DDataValue()][x][layer][z];
    }

    private static VoxelShape[][][][] load() {
        VoxelShape[][][][] result = new VoxelShape[4][2][3][2];
        String path = "/assets/kingdoms/models/block/charon_statue/collision.json";
        try (var stream = CharonStatueShapes.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Charon collision resource " + path);
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                for (int x = 0; x < 2; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 2; z++) {
                            VoxelShape north = Shapes.empty();
                            for (var entry : root.getAsJsonArray(x + "_" + y + "_" + z)) {
                                var a = entry.getAsJsonArray();
                                north = Shapes.joinUnoptimized(north, Block.box(
                                        a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                                        a.get(3).getAsDouble(), a.get(4).getAsDouble(), a.get(5).getAsDouble()
                                ), BooleanOp.OR);
                            }
                            north = north.optimize();
                            for (Direction facing : Direction.Plane.HORIZONTAL) {
                                result[facing.get2DDataValue()][x][y][z] = rotate(north, facing);
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Charon statue collision", exception);
        }
        return result;
    }

    private static VoxelShape rotate(VoxelShape north, Direction facing) {
        VoxelShape[] shape = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            VoxelShape box = switch (facing) {
                case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
                case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
                case WEST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
                default -> Shapes.box(x0, y0, z0, x1, y1, z1);
            };
            shape[0] = Shapes.joinUnoptimized(shape[0], box, BooleanOp.OR);
        });
        return shape[0].optimize();
    }

    private CharonStatueShapes() {
    }
}
