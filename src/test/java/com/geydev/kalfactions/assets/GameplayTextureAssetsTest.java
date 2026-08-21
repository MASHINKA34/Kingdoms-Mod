package com.geydev.kalfactions.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void infernalKeyTexturesAreAnimatedOpaquePixelArtAssembly() throws IOException {
        List<String> names = List.of(
                "infernal_key_bow_fragment",
                "infernal_key_shaft_fragment",
                "infernal_key_bit_fragment",
                "infernal_key"
        );

        for (String name : names) {
            String texturePath = "/assets/kingdoms/textures/item/" + name + ".png";
            BufferedImage image = read(texturePath);
            assertEquals(64, image.getWidth());
            assertEquals(512, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(6, readBytes(texturePath)[25]);
            assertTrue(hasBinaryAlpha(image));
            assertTrue(opaqueColors(image).size() <= 16);

            for (int frame = 0; frame < 8; frame++) {
                assertEquals(0, image.getRGB(0, frame * 64) >>> 24);
                assertEquals(0, image.getRGB(63, frame * 64) >>> 24);
                assertEquals(0, image.getRGB(0, frame * 64 + 63) >>> 24);
                assertEquals(0, image.getRGB(63, frame * 64 + 63) >>> 24);
                assertTrue(frameHasOpaquePixels(image, frame));
            }

            String metadata = readText(texturePath + ".mcmeta");
            assertTrue(metadata.contains("\"frametime\": 3"));
            assertTrue(metadata.contains("\"interpolate\": false"));
            assertTrue(metadata.contains("\"frames\": [0, 1, 2, 3, 4, 5, 6, 7]"));

            String model = readText("/assets/kingdoms/models/item/" + name + ".json");
            assertTrue(model.contains("\"parent\": \"minecraft:item/generated\""));
            assertTrue(model.contains("\"layer0\": \"kingdoms:item/" + name + "\""));
        }

        BufferedImage bow = read("/assets/kingdoms/textures/item/infernal_key_bow_fragment.png");
        BufferedImage shaft = read("/assets/kingdoms/textures/item/infernal_key_shaft_fragment.png");
        BufferedImage bit = read("/assets/kingdoms/textures/item/infernal_key_bit_fragment.png");
        BufferedImage key = read("/assets/kingdoms/textures/item/infernal_key.png");

        assertStableFrameMask(bow);
        assertStableFrameMask(shaft);
        assertStableFrameMask(bit);
        assertEquals(0, changedPixels(bow, 0, 7));
        assertEquals(0, changedPixels(shaft, 0, 7));
        assertEquals(0, changedPixels(bit, 0, 7));
        assertEquals(0, changedPixels(key, 0, 7));
        assertTrue(changedPixels(bow, 0, 1) > 0);
        assertTrue(changedPixels(shaft, 0, 3) > 0);
        assertTrue(changedPixels(bit, 0, 5) > 0);
        assertTrue(changedPixels(key, 0, 1) > 0);

        for (int frame = 0; frame < 8; frame++) {
            BufferedImage composed = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            copyTranslatedFrameOverwrite(composed, bow, frame, -14, -13);
            copyTranslatedFrameOverwrite(composed, shaft, frame, 6, 7);
            copyTranslatedFrameOverwrite(composed, bit, frame, 17, 17);

            int extraPixels = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int expected = composed.getRGB(x, y);
                    int actual = key.getRGB(x, frame * 64 + y);
                    if ((expected >>> 24) != 0) {
                        assertEquals(expected >>> 24, actual >>> 24, frame + ":" + x + "," + y);
                    } else if ((actual >>> 24) != 0) {
                        extraPixels++;
                    }
                }
            }
            assertEquals(frame == 5 || frame == 6 ? 2 : 0, extraPixels, "frame " + frame);
        }

        assertTrue(darkPixels(key) > hotPixels(key) * 3);
        Path source = Path.of("art/aseprite/items/infernal_key_set.aseprite");
        assertTrue(Files.isRegularFile(source));
        assertTrue(Files.size(source) > 0);
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
            assertEquals(64, image.getWidth());
            assertEquals(512, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(6, readBytes(texturePath)[25]);
            assertTrue(hasBinaryAlpha(image));
            assertTrue(opaqueColors(image).size() <= 21);

            for (int frame = 0; frame < 8; frame++) {
                assertEquals(0, image.getRGB(0, frame * 64) >>> 24);
                assertEquals(0, image.getRGB(63, frame * 64) >>> 24);
                assertEquals(0, image.getRGB(0, frame * 64 + 63) >>> 24);
                assertEquals(0, image.getRGB(63, frame * 64 + 63) >>> 24);
                assertTrue(frameHasOpaquePixels(image, frame));
            }

            String metadata = readText(texturePath + ".mcmeta");
            assertTrue(metadata.contains("\"frametime\": 3"));
            assertTrue(metadata.contains("\"interpolate\": false"));
            assertTrue(metadata.contains("\"frames\": [0, 1, 2, 3, 4, 5, 6, 7]"));

            String model = readText("/assets/kingdoms/models/item/" + name + ".json");
            assertTrue(model.contains("\"parent\": \"minecraft:item/generated\""));
            assertTrue(model.contains("\"layer0\": \"kingdoms:item/" + name + "\""));
        }

        BufferedImage bow = read("/assets/kingdoms/textures/item/mossy_key_bow_fragment.png");
        BufferedImage shaft = read("/assets/kingdoms/textures/item/mossy_key_shaft_fragment.png");
        BufferedImage bit = read("/assets/kingdoms/textures/item/mossy_key_bit_fragment.png");
        BufferedImage key = read("/assets/kingdoms/textures/item/mossy_key.png");
        assertEquals(0, changedPixels(bow, 0, 7));
        assertEquals(0, changedPixels(shaft, 0, 7));
        assertEquals(0, changedPixels(bit, 0, 7));
        assertEquals(0, changedPixels(key, 0, 7));
        assertTrue(changedPixels(bow, 0, 1) > 0);
        assertTrue(changedPixels(shaft, 0, 3) > 0);
        assertTrue(changedPixels(bit, 0, 5) > 0);

        for (int frame = 0; frame < 8; frame++) {
            BufferedImage composed = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            copyTranslatedFrameOverwrite(composed, bow, frame, -16, -15);
            copyTranslatedFrameOverwrite(composed, shaft, frame, 3, 2);
            copyTranslatedFrameOverwrite(composed, bit, frame, 18, 15);

            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    assertEquals(composed.getRGB(x, y), key.getRGB(x, frame * 64 + y), frame + ":" + x + "," + y);
                }
            }
        }

        Set<Integer> colors = opaqueColors(key);
        assertTrue(colors.contains(0xFFA45E3D));
        assertTrue(colors.contains(0xFF8D9688));
        assertTrue(colors.contains(0xFF4D7334));
        assertTrue(colors.contains(0xFFF2B84B));
        String keyModel = readText("/assets/kingdoms/models/item/mossy_key.json");
        assertTrue(keyModel.contains("\"rotation\": [0, -90, -155]"));
        assertTrue(keyModel.contains("\"rotation\": [0, 90, 155]"));
        assertTrue(keyModel.contains("\"scale\": [0.55, 0.55, 0.55]"));
        Path source = Path.of("art/aseprite/items/mossy_key_set.aseprite");
        assertTrue(Files.isRegularFile(source));
        assertTrue(Files.size(source) > 0);
    }

    @Test
    void ghostKeyTexturesAreAnimatedExactPixelArtAssembly() throws IOException {
        List<String> names = List.of(
                "ghost_key_bow_fragment",
                "ghost_key_shaft_fragment",
                "ghost_key_bit_fragment",
                "ghost_key"
        );

        for (String name : names) {
            String texturePath = "/assets/kingdoms/textures/item/" + name + ".png";
            BufferedImage image = read(texturePath);
            assertEquals(64, image.getWidth());
            assertEquals(512, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            assertEquals(6, readBytes(texturePath)[25]);
            assertTrue(hasBinaryAlpha(image));
            assertTrue(opaqueColors(image).size() <= 17);

            for (int frame = 0; frame < 8; frame++) {
                assertEquals(0, image.getRGB(0, frame * 64) >>> 24);
                assertEquals(0, image.getRGB(63, frame * 64) >>> 24);
                assertEquals(0, image.getRGB(0, frame * 64 + 63) >>> 24);
                assertEquals(0, image.getRGB(63, frame * 64 + 63) >>> 24);
                assertTrue(frameHasOpaquePixels(image, frame));
            }

            String metadata = readText(texturePath + ".mcmeta");
            assertTrue(metadata.contains("\"frametime\": 3"));
            assertTrue(metadata.contains("\"interpolate\": false"));
            assertTrue(metadata.contains("\"frames\": [0, 1, 2, 3, 4, 5, 6, 7]"));

            String model = readText("/assets/kingdoms/models/item/" + name + ".json");
            assertTrue(model.contains("\"parent\": \"minecraft:item/generated\""));
            assertTrue(model.contains("\"layer0\": \"kingdoms:item/" + name + "\""));
        }

        BufferedImage bow = read("/assets/kingdoms/textures/item/ghost_key_bow_fragment.png");
        BufferedImage shaft = read("/assets/kingdoms/textures/item/ghost_key_shaft_fragment.png");
        BufferedImage bit = read("/assets/kingdoms/textures/item/ghost_key_bit_fragment.png");
        BufferedImage key = read("/assets/kingdoms/textures/item/ghost_key.png");

        assertStableFrameMask(bow);
        assertStableFrameMask(shaft);
        assertStableFrameMask(bit);
        assertTrue(changedPixels(bow, 0, 1) > 0);
        assertTrue(changedPixels(shaft, 0, 1) > 0);
        assertTrue(changedPixels(bit, 0, 1) > 0);
        assertTrue(changedPixels(key, 0, 1) > 0);

        for (int frame = 0; frame < 8; frame++) {
            BufferedImage composed = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            copyTranslatedFrame(composed, bow, frame, 16, -18);
            copyTranslatedFrame(composed, shaft, frame, -2, 6);
            copyTranslatedFrame(composed, bit, frame, -18, 23);

            int extraPixels = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int expected = composed.getRGB(x, y);
                    int actual = key.getRGB(x, frame * 64 + y);
                    if ((expected >>> 24) != 0) {
                        assertEquals(expected, actual, frame + ":" + x + "," + y);
                    } else if ((actual >>> 24) != 0) {
                        extraPixels++;
                    }
                }
            }

            int expectedWisps = switch (frame) {
                case 3 -> 1;
                case 4 -> 4;
                case 5 -> 3;
                default -> 0;
            };
            assertEquals(expectedWisps, extraPixels, "frame " + frame);
        }

        String modItems = Files.readString(Path.of(
                "src/main/java/com/geydev/kalfactions/registry/ModItems.java"
        ));
        String creativeTabs = Files.readString(Path.of(
                "src/main/java/com/geydev/kalfactions/registry/ModCreativeTabs.java"
        ));
        List<String> fields = List.of(
                "GHOST_KEY_BOW_FRAGMENT",
                "GHOST_KEY_SHAFT_FRAGMENT",
                "GHOST_KEY_BIT_FRAGMENT",
                "GHOST_KEY"
        );
        for (int i = 0; i < names.size(); i++) {
            assertTrue(modItems.contains("\"" + names.get(i) + "\""));
            assertTrue(creativeTabs.contains("ModItems." + fields.get(i) + ".get()"));
            assertTrue(readText("/assets/kingdoms/lang/en_us.json")
                    .contains("\"item.kingdoms." + names.get(i) + "\""));
            assertTrue(readText("/assets/kingdoms/lang/ru_ru.json")
                    .contains("\"item.kingdoms." + names.get(i) + "\""));
        }
        Path source = Path.of("art/aseprite/items/ghost_key_set.aseprite");
        assertTrue(Files.isRegularFile(source));
        assertTrue(Files.size(source) > 0);
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

    private static boolean frameHasOpaquePixels(BufferedImage image, int frame) {
        int size = image.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((image.getRGB(x, frame * size + y) >>> 24) == 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertStableFrameMask(BufferedImage image) {
        int size = image.getWidth();
        for (int frame = 1; frame < 8; frame++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    assertEquals(
                            image.getRGB(x, y) >>> 24,
                            image.getRGB(x, frame * size + y) >>> 24,
                            frame + ":" + x + "," + y
                    );
                }
            }
        }
    }

    private static int changedPixels(BufferedImage image, int firstFrame, int secondFrame) {
        int changed = 0;
        int size = image.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (image.getRGB(x, firstFrame * size + y)
                        != image.getRGB(x, secondFrame * size + y)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private static void copyTranslatedFrame(
            BufferedImage target,
            BufferedImage source,
            int frame,
            int dx,
            int dy
    ) {
        int size = source.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = source.getRGB(x, frame * size + y);
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

    private static void copyTranslatedFrameOverwrite(
            BufferedImage target,
            BufferedImage source,
            int frame,
            int dx,
            int dy
    ) {
        int size = source.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = source.getRGB(x, frame * size + y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                int targetX = x + dx;
                int targetY = y + dy;
                assertTrue(targetX >= 0 && targetX < target.getWidth());
                assertTrue(targetY >= 0 && targetY < target.getHeight());
                target.setRGB(targetX, targetY, argb);
            }
        }
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
