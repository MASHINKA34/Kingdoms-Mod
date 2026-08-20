package com.geydev.kalfactions.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    void minibossTokensAreGameReadyItemTextures() throws IOException {
        for (String token : List.of(
                "miniboss_token_ghost",
                "miniboss_token_sculk",
                "miniboss_token_nether",
                "miniboss_token_lush_caves",
                "miniboss_token_end"
        )) {
            BufferedImage image = read("/assets/kingdoms/textures/item/" + token + ".png");

            assertSquare(image, 32);
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertTrue(hasOpaquePixels(image));
            assertTrue(readText("/assets/kingdoms/models/item/" + token + ".json")
                    .contains("\"layer0\": \"kingdoms:item/" + token + "\""));
        }
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

    @Test
    void infernalKeyTexturesAreExactOpaquePixelArtAssembly() throws IOException {
        List<String> names = List.of(
                "infernal_key_bow_fragment",
                "infernal_key_shaft_fragment",
                "infernal_key_bit_fragment",
                "infernal_key"
        );

        for (String name : names) {
            String texturePath = "/assets/kingdoms/textures/item/" + name + ".png";
            BufferedImage image = read(texturePath);
            assertSquare(image, 32);
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(6, readBytes(texturePath)[25]);
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertTrue(hasOpaquePixels(image));
            assertTrue(hasBinaryAlpha(image));
            assertTrue(opaqueColors(image).size() <= 10);
            assertTrue(readText("/assets/kingdoms/models/item/" + name + ".json")
                    .contains("\"layer0\": \"kingdoms:item/" + name + "\""));
        }

        BufferedImage bow = read("/assets/kingdoms/textures/item/infernal_key_bow_fragment.png");
        BufferedImage shaft = read("/assets/kingdoms/textures/item/infernal_key_shaft_fragment.png");
        BufferedImage bit = read("/assets/kingdoms/textures/item/infernal_key_bit_fragment.png");
        BufferedImage key = read("/assets/kingdoms/textures/item/infernal_key.png");
        BufferedImage composed = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

        copyTranslated(composed, bow, -8, 0);
        copyTranslated(composed, shaft, 3, -1);
        copyTranslated(composed, bit, 12, 1);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(composed.getRGB(x, y), key.getRGB(x, y), x + "," + y);
            }
        }

        assertTrue(darkPixels(key) > hotPixels(key) * 3);
        assertTrue(hotPixelsAreEmbedded(key));
    }

    @Test
    void mossyKeyTexturesAreExactOpaquePixelArtAssembly() throws IOException {
        List<String> names = List.of(
                "mossy_key_bow_fragment",
                "mossy_key_shaft_fragment",
                "mossy_key_bit_fragment",
                "mossy_key"
        );

        for (String name : names) {
            String texturePath = "/assets/kingdoms/textures/item/" + name + ".png";
            BufferedImage image = read(texturePath);
            assertSquare(image, 32);
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(6, readBytes(texturePath)[25]);
            assertEquals(0, image.getRGB(0, 0) >>> 24);
            assertEquals(0, image.getRGB(31, 0) >>> 24);
            assertEquals(0, image.getRGB(0, 31) >>> 24);
            assertEquals(0, image.getRGB(31, 31) >>> 24);
            assertTrue(hasOpaquePixels(image));
            assertTrue(hasBinaryAlpha(image));
            assertTrue(opaqueColors(image).size() <= 13);
            assertTrue(readText("/assets/kingdoms/models/item/" + name + ".json")
                    .contains("\"layer0\": \"kingdoms:item/" + name + "\""));
        }

        BufferedImage bow = read("/assets/kingdoms/textures/item/mossy_key_bow_fragment.png");
        BufferedImage shaft = read("/assets/kingdoms/textures/item/mossy_key_shaft_fragment.png");
        BufferedImage bit = read("/assets/kingdoms/textures/item/mossy_key_bit_fragment.png");
        BufferedImage key = read("/assets/kingdoms/textures/item/mossy_key.png");
        BufferedImage composed = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

        copyTranslated(composed, bow, 0, -8);
        copyTranslated(composed, shaft, 0, 2);
        copyTranslated(composed, bit, 0, 11);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                assertEquals(composed.getRGB(x, y), key.getRGB(x, y), x + "," + y);
            }
        }

        Set<Integer> colors = opaqueColors(key);
        assertTrue(colors.contains(0xFF9F6543));
        assertTrue(colors.contains(0xFF93998D));
        assertTrue(colors.contains(0xFF5E6F33));
        assertTrue(colors.contains(0xFFFFDA50));
        String keyModel = readText("/assets/kingdoms/models/item/mossy_key.json");
        assertTrue(keyModel.contains("\"rotation\": [0, -90, -155]"));
        assertTrue(keyModel.contains("\"rotation\": [0, 90, 155]"));
        assertTrue(keyModel.contains("\"scale\": [0.55, 0.55, 0.55]"));
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

    private static byte[] readBytes(String path) throws IOException {
        try (InputStream input = GameplayTextureAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return input.readAllBytes();
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

    private static boolean hasBinaryAlpha(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha != 0 && alpha != 255) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Set<Integer> opaqueColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 255) {
                    colors.add(argb);
                }
            }
        }
        return colors;
    }

    private static void copyTranslated(BufferedImage target, BufferedImage source, int dx, int dy) {
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int targetX = x + dx;
                int targetY = y + dy;
                assertTrue(targetX >= 0 && targetX < target.getWidth());
                assertTrue(targetY >= 0 && targetY < target.getHeight());
                int existing = target.getRGB(targetX, targetY);
                if ((existing >>> 24) != 0) {
                    assertEquals(existing, argb, targetX + "," + targetY);
                }
                target.setRGB(targetX, targetY, argb);
            }
        }
    }

    private static int darkPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = (argb >> 16) & 255;
                int green = (argb >> 8) & 255;
                if ((argb >>> 24) == 255 && red < 120 && green < 100) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int hotPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = (argb >> 16) & 255;
                int green = (argb >> 8) & 255;
                if ((argb >>> 24) == 255 && red > 190 && green > 35) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean hotPixelsAreEmbedded(BufferedImage image) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = (argb >> 16) & 255;
                int green = (argb >> 8) & 255;
                if ((argb >>> 24) != 255 || red <= 190 || green <= 35) {
                    continue;
                }
                int opaqueNeighbors = 0;
                for (int[] direction : directions) {
                    int neighborX = x + direction[0];
                    int neighborY = y + direction[1];
                    if (neighborX >= 0 && neighborX < image.getWidth()
                            && neighborY >= 0 && neighborY < image.getHeight()
                            && (image.getRGB(neighborX, neighborY) >>> 24) == 255) {
                        opaqueNeighbors++;
                    }
                }
                if (opaqueNeighbors < 2) {
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
