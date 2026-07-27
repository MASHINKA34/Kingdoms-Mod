package com.geydev.kalfactions.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class GameplayTextureAssetsTest {
    @Test
    void returnSigilIsAReadableTransparentItemTexture() throws IOException {
        BufferedImage image = read("/assets/kingdoms/textures/item/nether_return_sigil.png");

        assertSquare(image, 32);
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(0, image.getRGB(0, 0) >>> 24);
    }

    @Test
    void mapArchiveTableHasCompleteTextureSet() throws IOException {
        assertSquare(read("/assets/kingdoms/textures/block/xaero_map_archive_top.png"), 32);
        assertSquare(read("/assets/kingdoms/textures/block/xaero_map_archive_side.png"), 32);
        assertSquare(read("/assets/kingdoms/textures/block/xaero_map_archive_leg.png"), 32);
    }

    @Test
    void archiveItemsAreReadableTransparentTextures() throws IOException {
        BufferedImage mapArchive = read("/assets/kingdoms/textures/item/xaero_map_archive.png");
        BufferedImage warArchive = read("/assets/kingdoms/textures/item/war_archive.png");

        assertSquare(mapArchive, 256);
        assertSquare(warArchive, 256);
        assertTrue(mapArchive.getColorModel().hasAlpha());
        assertTrue(warArchive.getColorModel().hasAlpha());
        assertEquals(0, mapArchive.getRGB(0, 0) >>> 24);
        assertEquals(0, warArchive.getRGB(0, 0) >>> 24);
        assertTrue(hasOpaquePixels(mapArchive));
        assertTrue(hasOpaquePixels(warArchive));
    }

    @Test
    void drillItemIsAReadableTransparentTexture() throws IOException {
        BufferedImage drill = read("/assets/kingdoms/textures/item/drill.png");

        assertSquare(drill, 64);
        assertTrue(drill.getColorModel().hasAlpha());
        assertEquals(0, drill.getRGB(0, 0) >>> 24);
        assertTrue(hasOpaquePixels(drill));
    }

    @Test
    void quarryAssetsAreGameReadyTextures() throws IOException {
        BufferedImage activator = read("/assets/kingdoms/textures/item/quarry_activator.png");
        BufferedImage core = read("/assets/kingdoms/textures/block/quarry_core.png");
        BufferedImage gui = read("/assets/kingdoms/textures/gui/quarry/quarry_gui.png");

        assertSquare(activator, 256);
        assertSquare(core, 256);
        assertEquals(696, gui.getWidth());
        assertEquals(520, gui.getHeight());
        assertTrue(activator.getColorModel().hasAlpha());
        assertEquals(0, activator.getRGB(0, 0) >>> 24);
        assertEquals(0, activator.getRGB(255, 0) >>> 24);
        assertEquals(0, activator.getRGB(0, 255) >>> 24);
        assertEquals(0, activator.getRGB(255, 255) >>> 24);
        assertTrue(hasOpaquePixels(activator));
        assertTrue(isFullyOpaque(core));
        assertTrue(isFullyOpaque(gui));
        assertTrue(readText("/assets/kingdoms/models/item/quarry_activator.json")
                .contains("\"layer0\": \"kingdoms:item/quarry_activator\""));
        assertTrue(readText("/assets/kingdoms/models/block/quarry_core.json")
                .contains("\"all\": \"kingdoms:block/quarry_core\""));
        assertTrue(readText("/assets/kingdoms/models/item/quarry_core.json")
                .contains("\"parent\": \"kingdoms:block/quarry_core\""));
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream input = GameplayTextureAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            return image;
        }
    }

    private static String readText(String path) throws IOException {
        try (InputStream input = GameplayTextureAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean hasOpaquePixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFullyOpaque(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 255) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void assertSquare(BufferedImage image, int size) {
        assertEquals(size, image.getWidth());
        assertEquals(size, image.getHeight());
    }
}
