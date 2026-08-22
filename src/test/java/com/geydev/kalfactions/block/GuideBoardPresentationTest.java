package com.geydev.kalfactions.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

final class GuideBoardPresentationTest {
    @Test
    void modelOccupiesExactlyThreeByTwoBlocksWithThickSeparatedElements() throws IOException {
        JsonObject model = readJson("/assets/kingdoms/models/block/guide_board.json");
        JsonArray elements = model.getAsJsonArray("elements");

        assertEquals(41, elements.size());
        assertFalse(model.get("ambientocclusion").getAsBoolean());
        assertEquals("kingdoms:block/guide_board_atlas", model.getAsJsonObject("textures").get("0").getAsString());
        for (var entry : elements) {
            JsonObject element = entry.getAsJsonObject();
            JsonArray from = element.getAsJsonArray("from");
            JsonArray to = element.getAsJsonArray("to");
            assertTrue(from.get(0).getAsDouble() >= -16.0D);
            assertTrue(to.get(0).getAsDouble() <= 32.0D);
            assertTrue(from.get(1).getAsDouble() >= 0.0D);
            assertTrue(to.get(1).getAsDouble() <= 32.0D);
            assertTrue(from.get(2).getAsDouble() >= 0.0D);
            assertTrue(to.get(2).getAsDouble() <= 16.0D);
            for (int axis = 0; axis < 3; axis++) {
                assertTrue(to.get(axis).getAsDouble() - from.get(axis).getAsDouble() >= 0.25D);
            }
            for (var face : element.getAsJsonObject("faces").entrySet()) {
                assertFalse(face.getValue().getAsJsonObject().has("cullface"));
                assertEquals("#0", face.getValue().getAsJsonObject().get("texture").getAsString());
            }
        }
    }

    @Test
    void atlasIsCrispAndUsesABoundedPixelPalette() throws IOException {
        BufferedImage image = readImage("/assets/kingdoms/textures/block/guide_board_atlas.png");
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getRGB(x, y));
            }
        }

        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
        assertTrue(colors.size() >= 40);
        assertTrue(colors.size() <= 96);
    }

    @Test
    void everyMultiblockPartHasBoundedDirectionalCollision() {
        for (GuideBoardBlock.GuideBoardPart part : GuideBoardBlock.GuideBoardPart.values()) {
            for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    VoxelShape shape = BlockShapes.guideBoard(facing, part, half);
                    assertFalse(shape.isEmpty());
                    AABB bounds = shape.bounds();
                    assertTrue(bounds.minX >= 0.0D && bounds.maxX <= 1.0D);
                    assertTrue(bounds.minY >= 0.0D && bounds.maxY <= 1.0D);
                    assertTrue(bounds.minZ >= 0.0D && bounds.maxZ <= 1.0D);
                    assertTrue(bounds.getXsize() < 1.0D || bounds.getYsize() < 1.0D || bounds.getZsize() < 1.0D);
                }
            }
        }
    }

    @Test
    void blockstatesAndItemTransformsCoverEveryOrientationAndView() throws IOException {
        JsonObject variants = readJson("/assets/kingdoms/blockstates/guide_board.json")
                .getAsJsonObject("variants");
        JsonObject item = readJson("/assets/kingdoms/models/item/guide_board.json");

        assertEquals(24, variants.size());
        assertEquals("kingdoms:block/guide_board", item.get("parent").getAsString());
        JsonObject display = item.getAsJsonObject("display");
        for (String view : Set.of(
                "gui",
                "ground",
                "fixed",
                "thirdperson_righthand",
                "thirdperson_lefthand",
                "firstperson_righthand",
                "firstperson_lefthand"
        )) {
            assertTrue(display.has(view));
        }
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream stream = GuideBoardPresentationTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static BufferedImage readImage(String resource) throws IOException {
        try (InputStream stream = GuideBoardPresentationTest.class.getResourceAsStream(resource)) {
            assertTrue(stream != null);
            return ImageIO.read(stream);
        }
    }
}
