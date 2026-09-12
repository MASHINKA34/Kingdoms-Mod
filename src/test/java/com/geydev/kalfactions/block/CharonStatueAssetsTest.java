package com.geydev.kalfactions.block;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class CharonStatueAssetsTest {
    @Test
    void everyCellHasLocalGeometryAndOutwardWinding() throws Exception {
        var variants = json("/assets/kingdoms/blockstates/charon_statue.json").getAsJsonObject("variants");
        assertEquals(48, variants.size());
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 2; z++) {
                    String path = "/assets/kingdoms/models/block/charon_statue/part_" + x + "_" + y + "_" + z;
                    var model = json(path + ".json");
                    assertEquals("neoforge:obj", model.get("loader").getAsString());
                    List<Vector3f> positions = obj(path + ".obj", "v ");
                    List<Vector3f> normals = obj(path + ".obj", "vn ");
                    assertFalse(positions.isEmpty());
                    assertEquals(positions.size(), normals.size());
                    for (Vector3f p : positions) {
                        assertTrue(p.x >= -1e-6 && p.y >= -1e-6 && p.z >= -1e-6
                                && p.x <= 1.000001 && p.y <= 1.000001 && p.z <= 1.000001, path + " spills into another cell");
                    }
                    for (int i = 0; i < positions.size(); i += 3) {
                        var edge = new Vector3f(positions.get(i + 1)).sub(positions.get(i));
                        var edge2 = new Vector3f(positions.get(i + 2)).sub(positions.get(i));
                        assertTrue(edge.cross(edge2).dot(normals.get(i)) > 0, path + " has an inverted or degenerate face");
                    }
                }
            }
        }
    }

    @Test
    void inventoryTransformKeepsTheWholeStatueInsideTheSlot() throws Exception {
        var model = json("/assets/kingdoms/models/item/charon_statue.json");
        var gui = model.getAsJsonObject("display").getAsJsonObject("gui");
        var r = gui.getAsJsonArray("rotation");
        var q = new Quaternionf().rotationXYZ((float) Math.toRadians(r.get(0).getAsFloat()),
                (float) Math.toRadians(r.get(1).getAsFloat()), (float) Math.toRadians(r.get(2).getAsFloat()));
        float scale = gui.getAsJsonArray("scale").get(0).getAsFloat();
        for (var v : obj("/assets/kingdoms/models/block/charon_statue/inventory.obj", "v ")) {
            v.sub(.5F, .5F, .5F).mul(scale).rotate(q);
            var translation = gui.getAsJsonArray("translation");
            v.add(translation.get(0).getAsFloat() / 16, translation.get(1).getAsFloat() / 16,
                    translation.get(2).getAsFloat() / 16);
            assertTrue(Math.abs(v.x) < .49F && Math.abs(v.y) < .49F, "Inventory silhouette is clipped: " + v);
        }
        assertEquals(8, model.getAsJsonObject("display").size());
    }

    @Test
    void collisionIsBoundedAndLeavesEmptySpaceAroundTheHood() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 3; y++) {
                    for (int z = 0; z < 2; z++) {
                        var shape = CharonStatueShapes.get(facing, x, y, z);
                        assertFalse(shape.isEmpty());
                        var b = shape.bounds();
                        assertTrue(b.minX >= 0 && b.minY >= 0 && b.minZ >= 0
                                && b.maxX <= 1 && b.maxY <= 1 && b.maxZ <= 1);
                    }
                }
            }
        }
        var hood = CharonStatueShapes.get(Direction.NORTH, 0, 2, 1);
        assertFalse(hood.toAabbs().stream().anyMatch(b -> b.contains(.05, .9, .95)), "Empty corner became a solid full block");
        var base = CharonStatueShapes.get(Direction.NORTH, 0, 0, 0);
        assertTrue(base.toAabbs().stream().anyMatch(b -> b.contains(.05, .02, .05)), "Pedestal edge has no collision");
    }

    private static JsonObject json(String path) throws Exception {
        try (var in = CharonStatueAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<Vector3f> obj(String path, String prefix) throws Exception {
        try (var in = CharonStatueAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            List<Vector3f> result = new ArrayList<>();
            for (String line : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList()) {
                if (line.startsWith(prefix)) {
                    String[] p = line.substring(prefix.length()).split(" ");
                    result.add(new Vector3f(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])));
                }
            }
            return result;
        }
    }
}
