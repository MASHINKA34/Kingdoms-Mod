package com.geydev.kalfactions.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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

    private static BufferedImage read(String path) throws IOException {
        try (InputStream input = GameplayTextureAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            return image;
        }
    }

    private static void assertSquare(BufferedImage image, int size) {
        assertEquals(size, image.getWidth());
        assertEquals(size, image.getHeight());
    }
}
