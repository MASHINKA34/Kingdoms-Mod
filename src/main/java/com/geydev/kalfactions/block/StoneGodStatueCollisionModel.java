package com.geydev.kalfactions.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Builds server-side collision directly from the same Blockbench cubes used by the renderer. */
final class StoneGodStatueCollisionModel {
    private static final double EPSILON = 1.0E-7D;
    private static final int MAX_HORIZONTAL_OFFSET = 3;

    private StoneGodStatueCollisionModel() {
    }

    static Map<Direction, List<Cell>> load(
            String modelName,
            @Nullable Float centerXOverride,
            @Nullable Float centerZOverride
    ) {
        String path = "/assets/kingdoms/models/block/" + modelName + ".bbmodel";
        try (var stream = StoneGodStatueCollisionModel.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing stone statue collision model " + path);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                List<ModelBox> boxes = readBoxes(root.getAsJsonArray("elements"));
                Bounds bounds = Bounds.of(boxes);
                double centerX = centerXOverride != null ? centerXOverride : bounds.centerX();
                double centerZ = centerZOverride != null ? centerZOverride : bounds.centerZ();

                EnumMap<Direction, List<Cell>> cellsByDirection = new EnumMap<>(Direction.class);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    cellsByDirection.put(
                            direction,
                            createCells(boxes, bounds.minY(), centerX, centerZ, direction)
                    );
                }
                return Map.copyOf(cellsByDirection);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not build collision for " + modelName, exception);
        }
    }

    private static List<ModelBox> readBoxes(JsonArray elements) {
        List<ModelBox> boxes = new ArrayList<>(elements.size());
        for (var entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            if (!hasRenderedFace(element.getAsJsonObject("faces"))) {
                continue;
            }
            double[] from = vector(element.getAsJsonArray("from"));
            double[] to = vector(element.getAsJsonArray("to"));
            double[] origin = element.has("origin")
                    ? vector(element.getAsJsonArray("origin"))
                    : new double[]{0.0D, 0.0D, 0.0D};
            double rotationZ = element.has("rotation")
                    ? element.getAsJsonArray("rotation").get(2).getAsDouble()
                    : 0.0D;

            double radians = Math.toRadians(rotationZ);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (double x : new double[]{from[0], to[0]}) {
                for (double y : new double[]{from[1], to[1]}) {
                    double rotatedX = origin[0]
                            + (x - origin[0]) * cosine
                            - (y - origin[1]) * sine;
                    double rotatedY = origin[1]
                            + (x - origin[0]) * sine
                            + (y - origin[1]) * cosine;
                    minX = Math.min(minX, rotatedX);
                    minY = Math.min(minY, rotatedY);
                    maxX = Math.max(maxX, rotatedX);
                    maxY = Math.max(maxY, rotatedY);
                }
            }
            boxes.add(new ModelBox(
                    minX,
                    minY,
                    Math.min(from[2], to[2]),
                    maxX,
                    maxY,
                    Math.max(from[2], to[2])
            ));
        }
        if (boxes.isEmpty()) {
            throw new IllegalStateException("Stone statue model contains no rendered cubes");
        }
        return List.copyOf(boxes);
    }

    private static boolean hasRenderedFace(JsonObject faces) {
        for (var faceEntry : faces.entrySet()) {
            JsonObject face = faceEntry.getValue().getAsJsonObject();
            if (face.has("texture") && !face.get("texture").isJsonNull()) {
                return true;
            }
        }
        return false;
    }

    private static List<Cell> createCells(
            List<ModelBox> boxes,
            double minModelY,
            double centerModelX,
            double centerModelZ,
            Direction direction
    ) {
        Map<CellKey, VoxelShape> shapes = new HashMap<>();
        for (ModelBox box : boxes) {
            AABB worldBox = new AABB(
                    0.5D + (box.minX() - centerModelX) / 16.0D,
                    (box.minY() - minModelY) / 16.0D,
                    0.5D + (box.minZ() - centerModelZ) / 16.0D,
                    0.5D + (box.maxX() - centerModelX) / 16.0D,
                    (box.maxY() - minModelY) / 16.0D,
                    0.5D + (box.maxZ() - centerModelZ) / 16.0D
            );
            addBox(shapes, rotate(worldBox, direction));
        }

        List<Cell> cells = new ArrayList<>(shapes.size());
        shapes.forEach((key, shape) -> cells.add(new Cell(
                key.offsetX(),
                key.segment(),
                key.offsetZ(),
                shape.optimize()
        )));
        cells.sort(Comparator.comparingInt(Cell::segment)
                .thenComparingInt(Cell::offsetX)
                .thenComparingInt(Cell::offsetZ));
        return List.copyOf(cells);
    }

    private static AABB rotate(AABB box, Direction direction) {
        return switch (direction) {
            case SOUTH -> new AABB(
                    1.0D - box.maxX,
                    box.minY,
                    1.0D - box.maxZ,
                    1.0D - box.minX,
                    box.maxY,
                    1.0D - box.minZ
            );
            case WEST -> new AABB(
                    box.minZ,
                    box.minY,
                    1.0D - box.maxX,
                    box.maxZ,
                    box.maxY,
                    1.0D - box.minX
            );
            case EAST -> new AABB(
                    1.0D - box.maxZ,
                    box.minY,
                    box.minX,
                    1.0D - box.minZ,
                    box.maxY,
                    box.maxX
            );
            default -> box;
        };
    }

    private static void addBox(Map<CellKey, VoxelShape> shapes, AABB box) {
        int minX = Mth.floor(box.minX + EPSILON);
        int maxX = Mth.ceil(box.maxX - EPSILON) - 1;
        int minY = Mth.floor(box.minY + EPSILON);
        int maxY = Mth.ceil(box.maxY - EPSILON) - 1;
        int minZ = Mth.floor(box.minZ + EPSILON);
        int maxZ = Mth.ceil(box.maxZ - EPSILON) - 1;
        if (minX < -MAX_HORIZONTAL_OFFSET || maxX > MAX_HORIZONTAL_OFFSET
                || minZ < -MAX_HORIZONTAL_OFFSET || maxZ > MAX_HORIZONTAL_OFFSET
                || minY < 0 || maxY >= StoneGodStatueBlock.HEIGHT) {
            throw new IllegalStateException("Stone statue collision exceeds its supported field: " + box);
        }

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double localMinX = Math.max(0.0D, box.minX - x);
                    double localMinY = Math.max(0.0D, box.minY - y);
                    double localMinZ = Math.max(0.0D, box.minZ - z);
                    double localMaxX = Math.min(1.0D, box.maxX - x);
                    double localMaxY = Math.min(1.0D, box.maxY - y);
                    double localMaxZ = Math.min(1.0D, box.maxZ - z);
                    if (localMaxX - localMinX <= EPSILON
                            || localMaxY - localMinY <= EPSILON
                            || localMaxZ - localMinZ <= EPSILON) {
                        continue;
                    }
                    CellKey key = new CellKey(x, y, z);
                    VoxelShape piece = Shapes.box(
                            localMinX,
                            localMinY,
                            localMinZ,
                            localMaxX,
                            localMaxY,
                            localMaxZ
                    );
                    shapes.merge(key, piece, Shapes::or);
                }
            }
        }
    }

    private static double[] vector(JsonArray array) {
        return new double[]{
                array.get(0).getAsDouble(),
                array.get(1).getAsDouble(),
                array.get(2).getAsDouble()
        };
    }

    record Cell(int offsetX, int segment, int offsetZ, VoxelShape shape) {
    }

    private record CellKey(int offsetX, int segment, int offsetZ) {
    }

    private record ModelBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    private record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        private static Bounds of(List<ModelBox> boxes) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (ModelBox box : boxes) {
                minX = Math.min(minX, box.minX());
                minY = Math.min(minY, box.minY());
                minZ = Math.min(minZ, box.minZ());
                maxX = Math.max(maxX, box.maxX());
                maxY = Math.max(maxY, box.maxY());
                maxZ = Math.max(maxZ, box.maxZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private double centerX() {
            return (minX + maxX) * 0.5D;
        }

        private double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }
    }
}
