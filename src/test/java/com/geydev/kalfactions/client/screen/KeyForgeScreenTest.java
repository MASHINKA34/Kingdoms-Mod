package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.menu.KeyForgeMenu;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class KeyForgeScreenTest {
    private static final String SCULK_BACKGROUND =
            "/assets/kingdoms/textures/gui/sculk_key_forge/sculk_key_forge.png";
    private static final String SCULK_PROGRESS =
            "/assets/kingdoms/textures/gui/sculk_key_forge/progress.png";
    private static final String INFERNAL_BACKGROUND =
            "/assets/kingdoms/textures/gui/infernal_key_forge/infernal_key_forge.png";
    private static final String INFERNAL_PROGRESS =
            "/assets/kingdoms/textures/gui/infernal_key_forge/progress.png";
    private static final String MOSSY_BACKGROUND =
            "/assets/kingdoms/textures/gui/mossy_key_forge/mossy_key_forge.png";
    private static final String MOSSY_PROGRESS =
            "/assets/kingdoms/textures/gui/mossy_key_forge/progress.png";
    private static final String GHOST_BACKGROUND =
            "/assets/kingdoms/textures/gui/key_forge/ghost_key_forge.png";
    private static final String GHOST_IDLE =
            "/assets/kingdoms/textures/gui/key_forge/ghost_key_forge_idle.png";
    private static final String GHOST_READY =
            "/assets/kingdoms/textures/gui/key_forge/ghost_key_forge_ready.png";
    private static final String GHOST_PROGRESS =
            "/assets/kingdoms/textures/gui/key_forge/ghost_key_forge_progress.png";
    private static final String GHOST_COMPLETE =
            "/assets/kingdoms/textures/gui/key_forge/ghost_key_forge_complete.png";

    @Test
    void ghostLayoutUsesExactAnimationGridsAndServerProgressFrames() throws IOException {
        BufferedImage background = read(GHOST_BACKGROUND);
        BufferedImage idle = read(GHOST_IDLE);
        BufferedImage ready = read(GHOST_READY);
        BufferedImage progress = read(GHOST_PROGRESS);
        BufferedImage complete = read(GHOST_COMPLETE);

        assertEquals(KeyForgeScreen.PANEL_WIDTH, background.getWidth());
        assertEquals(KeyForgeScreen.GHOST_PANEL_HEIGHT, background.getHeight());
        assertSheet(idle, KeyForgeScreen.GHOST_IDLE_COLUMNS, 2);
        assertSheet(ready, KeyForgeScreen.GHOST_READY_COLUMNS, 2);
        assertSheet(progress, KeyForgeScreen.GHOST_PROGRESS_COLUMNS, 4);
        assertSheet(complete, KeyForgeScreen.GHOST_COMPLETE_COLUMNS, 2);
        assertTrue(hasBinaryAlpha(background));
        assertTrue(hasTransparentPixels(background));
        assertTrue(hasOpaquePixels(background));
        assertTrue(hasBinaryAlpha(idle));
        assertTrue(hasBinaryAlpha(ready));
        assertTrue(hasBinaryAlpha(progress));
        assertTrue(hasBinaryAlpha(complete));
        assertEquals(0, KeyForgeScreen.ghostProgressFrame(0.0F));
        assertEquals(6, KeyForgeScreen.ghostProgressFrame(0.25F));
        assertEquals(12, KeyForgeScreen.ghostProgressFrame(0.5F));
        assertEquals(17, KeyForgeScreen.ghostProgressFrame(0.75F));
        assertEquals(23, KeyForgeScreen.ghostProgressFrame(1.0F));
        assertEquals(35, KeyForgeMenu.GHOST_LEFT_INPUT_X);
        assertEquals(79, KeyForgeMenu.GHOST_CENTER_INPUT_X);
        assertEquals(123, KeyForgeMenu.GHOST_RIGHT_INPUT_X);
        assertEquals(29, KeyForgeMenu.GHOST_INPUT_Y);
        assertEquals(79, KeyForgeMenu.GHOST_OUTPUT_X);
        assertEquals(83, KeyForgeMenu.GHOST_OUTPUT_Y);
        assertEquals(120, KeyForgeMenu.GHOST_PLAYER_INVENTORY_Y);
        assertEquals(178, KeyForgeMenu.GHOST_PLAYER_HOTBAR_Y);
        assertTrue((270 - KeyForgeScreen.GHOST_PANEL_HEIGHT) / 2 >= 0);
    }

    @Test
    void sculkLayoutAndTexturesUseNativePixelDimensions() throws IOException {
        BufferedImage background = read(SCULK_BACKGROUND);
        BufferedImage progress = read(SCULK_PROGRESS);

        assertEquals(KeyForgeScreen.PANEL_WIDTH, background.getWidth());
        assertEquals(KeyForgeScreen.PANEL_HEIGHT, background.getHeight());
        assertEquals(KeyForgeScreen.SCULK_PROGRESS_WIDTH, progress.getWidth());
        assertEquals(KeyForgeScreen.SCULK_PROGRESS_TEXTURE_HEIGHT, progress.getHeight());
        assertTrue(isFullyOpaque(background));
        assertTrue(hasBinaryAlpha(progress));
        assertTrue(hasTransparentPixels(progress));
        assertTrue(hasOpaquePixels(progress));
        assertTrue(uniqueOpaqueColors(background) >= 20);
        assertTrue(countCyanPixels(background) >= 25);
        assertTrue(countBonePixels(background) >= 25);
        assertEquals(43, KeyForgeMenu.LEFT_INPUT_X);
        assertEquals(79, KeyForgeMenu.CENTER_INPUT_X);
        assertEquals(115, KeyForgeMenu.RIGHT_INPUT_X);
        assertEquals(27, KeyForgeMenu.INPUT_Y);
        assertEquals(79, KeyForgeMenu.OUTPUT_X);
        assertEquals(76, KeyForgeMenu.OUTPUT_Y);
        assertEquals(108, KeyForgeMenu.PLAYER_INVENTORY_Y);
    }

    @Test
    void panelCentersAtCommonGuiScaleViewports() {
        assertCentered(960, 540);
        assertCentered(640, 360);
        assertCentered(480, 270);
    }

    @Test
    void infernalLayoutUsesNativeDarkPixelArtAndBinaryProgressAlpha() throws IOException {
        BufferedImage background = read(INFERNAL_BACKGROUND);
        BufferedImage progress = read(INFERNAL_PROGRESS);

        assertEquals(KeyForgeScreen.PANEL_WIDTH, background.getWidth());
        assertEquals(KeyForgeScreen.PANEL_HEIGHT, background.getHeight());
        assertEquals(KeyForgeScreen.INFERNAL_PROGRESS_WIDTH, progress.getWidth());
        assertEquals(KeyForgeScreen.INFERNAL_PROGRESS_HEIGHT, progress.getHeight());
        assertTrue(isFullyOpaque(background));
        assertTrue(hasBinaryAlpha(progress));
        assertTrue(hasTransparentPixels(progress));
        assertTrue(hasOpaquePixels(progress));
        assertTrue(uniqueOpaqueColors(background) >= 60);
        assertTrue(countDarkPixels(background) >= 25_000);
        assertTrue(countHotPixels(background) >= 100);
        assertTrue(countCrimsonPixels(background) >= 350);
        assertTrue(countHotPixels(progress) >= 100);
        assertEquals(0xFFFFF0A0, progress.getRGB(91, 17));
        assertEquals(255, progress.getRGB(9, 0) >>> 24);
        assertEquals(255, progress.getRGB(45, 0) >>> 24);
        assertEquals(255, progress.getRGB(81, 0) >>> 24);
        assertEquals(255, progress.getRGB(45, 29) >>> 24);
        assertEquals(0xFF414047, background.getRGB(KeyForgeMenu.LEFT_INPUT_X, KeyForgeMenu.INPUT_Y));
        assertEquals(0xFF414047, background.getRGB(KeyForgeMenu.CENTER_INPUT_X, KeyForgeMenu.INPUT_Y));
        assertEquals(0xFF414047, background.getRGB(KeyForgeMenu.RIGHT_INPUT_X, KeyForgeMenu.INPUT_Y));
        assertEquals(0xFF414047, background.getRGB(KeyForgeMenu.OUTPUT_X, KeyForgeMenu.OUTPUT_Y));
    }

    @Test
    void infernalProgressSegmentsCropAtExactTickBoundaries() {
        assertEquals(0, KeyForgeScreen.segmentPixels(0, 0, 35, 14));
        assertEquals(1, KeyForgeScreen.segmentPixels(1, 0, 35, 14));
        assertEquals(14, KeyForgeScreen.segmentPixels(35, 0, 35, 14));
        assertEquals(0, KeyForgeScreen.segmentPixels(35, 35, 55, 13));
        assertEquals(7, KeyForgeScreen.segmentPixels(45, 35, 55, 13));
        assertEquals(13, KeyForgeScreen.segmentPixels(55, 35, 55, 13));
        assertEquals(0, KeyForgeScreen.segmentPixels(55, 55, 90, 20));
        assertEquals(20, KeyForgeScreen.segmentPixels(90, 55, 90, 20));
        assertEquals(0, KeyForgeScreen.segmentPixels(90, 90, 100, 10));
        assertEquals(10, KeyForgeScreen.segmentPixels(100, 90, 100, 10));
    }

    @Test
    void mossyLayoutUsesNativePixelArtClearSlotsAndCroppedVineProgress() throws IOException {
        BufferedImage background = read(MOSSY_BACKGROUND);
        BufferedImage progress = read(MOSSY_PROGRESS);

        assertEquals(KeyForgeScreen.PANEL_WIDTH, background.getWidth());
        assertEquals(KeyForgeScreen.PANEL_HEIGHT, background.getHeight());
        assertEquals(KeyForgeScreen.MOSSY_PROGRESS_WIDTH, progress.getWidth());
        assertEquals(KeyForgeScreen.MOSSY_PROGRESS_HEIGHT, progress.getHeight());
        assertTrue(isFullyOpaque(background));
        assertTrue(hasBinaryAlpha(progress));
        assertTrue(hasTransparentPixels(progress));
        assertTrue(hasOpaquePixels(progress));
        assertTrue(hasOpaquePixelInEveryColumn(progress));
        assertTrue(uniqueOpaqueColors(background) >= 30);
        assertTrue(countMossPixels(background) >= 40);
        assertTrue(countBronzePixels(background) >= 100);
        assertTrue(countGoldenPixels(background) >= 5);
        assertTrue(countGoldenPixels(progress) >= 10);
        assertSlotInteriorClear(background, KeyForgeMenu.LEFT_INPUT_X, KeyForgeMenu.INPUT_Y);
        assertSlotInteriorClear(background, KeyForgeMenu.CENTER_INPUT_X, KeyForgeMenu.INPUT_Y);
        assertSlotInteriorClear(background, KeyForgeMenu.RIGHT_INPUT_X, KeyForgeMenu.INPUT_Y);
        assertSlotInteriorClear(background, KeyForgeMenu.OUTPUT_X, KeyForgeMenu.OUTPUT_Y);
        assertEquals(0, KeyForgeScreen.segmentPixels(0, 0, 100, KeyForgeScreen.MOSSY_PROGRESS_WIDTH));
        assertEquals(1, KeyForgeScreen.segmentPixels(1, 0, 100, KeyForgeScreen.MOSSY_PROGRESS_WIDTH));
        assertEquals(45, KeyForgeScreen.segmentPixels(50, 0, 100, KeyForgeScreen.MOSSY_PROGRESS_WIDTH));
        assertEquals(90, KeyForgeScreen.segmentPixels(100, 0, 100, KeyForgeScreen.MOSSY_PROGRESS_WIDTH));
    }

    private static void assertCentered(int width, int height) {
        int left = (width - KeyForgeScreen.PANEL_WIDTH) / 2;
        int top = (height - KeyForgeScreen.PANEL_HEIGHT) / 2;
        assertEquals(width - KeyForgeScreen.PANEL_WIDTH, left * 2 + (width - KeyForgeScreen.PANEL_WIDTH) % 2);
        assertEquals(height - KeyForgeScreen.PANEL_HEIGHT, top * 2 + (height - KeyForgeScreen.PANEL_HEIGHT) % 2);
        assertTrue(left >= 0);
        assertTrue(top >= 0);
    }

    private static void assertSheet(BufferedImage image, int columns, int rows) {
        assertEquals(KeyForgeScreen.GHOST_OVERLAY_WIDTH * columns, image.getWidth());
        assertEquals(KeyForgeScreen.GHOST_OVERLAY_HEIGHT * rows, image.getHeight());
        assertTrue(hasTransparentPixels(image));
        assertTrue(hasOpaquePixels(image));
    }

    private static BufferedImage read(String path) throws IOException {
        try (InputStream input = KeyForgeScreenTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            return image;
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

    private static boolean hasTransparentPixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    return true;
                }
            }
        }
        return false;
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

    private static boolean hasOpaquePixelInEveryColumn(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            boolean opaque = false;
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) == 255) {
                    opaque = true;
                    break;
                }
            }
            if (!opaque) {
                return false;
            }
        }
        return true;
    }

    private static void assertSlotInteriorClear(BufferedImage image, int startX, int startY) {
        Set<Integer> allowed = Set.of(0xFF2A302A, 0xFF535B50, 0xFF454D44, 0xFF171C18);
        for (int y = startY; y < startY + 16; y++) {
            for (int x = startX; x < startX + 16; x++) {
                assertTrue(allowed.contains(image.getRGB(x, y)), "decorated slot pixel at " + x + "," + y);
            }
        }
    }

    private static int uniqueOpaqueColors(BufferedImage image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) == 255) {
                    colors.add(argb & 0xFFFFFF);
                }
            }
        }
        return colors.size();
    }

    private static int countCyanPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = rgb >> 16 & 255;
                int green = rgb >> 8 & 255;
                int blue = rgb & 255;
                if (green >= 90 && blue >= 85 && green > red * 2) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countBonePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = rgb >> 16 & 255;
                int green = rgb >> 8 & 255;
                int blue = rgb & 255;
                if (red >= 120 && green >= 110 && blue >= 80 && red > blue) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countDarkPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = rgb >> 16 & 255;
                int green = rgb >> 8 & 255;
                int blue = rgb & 255;
                if (red <= 50 && green <= 50 && blue <= 60) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countHotPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                int red = argb >> 16 & 255;
                int green = argb >> 8 & 255;
                int blue = argb & 255;
                if (alpha == 255 && red >= 140 && green >= 35 && green <= 170 && blue <= 100) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countCrimsonPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int red = rgb >> 16 & 255;
                int green = rgb >> 8 & 255;
                int blue = rgb & 255;
                if (red >= 45 && red > green * 2 && green >= blue) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countMossPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = argb >> 16 & 255;
                int green = argb >> 8 & 255;
                int blue = argb & 255;
                if ((argb >>> 24) == 255 && green >= 70 && green > red * 1.15 && green > blue * 1.25) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countBronzePixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = argb >> 16 & 255;
                int green = argb >> 8 & 255;
                int blue = argb & 255;
                if ((argb >>> 24) == 255 && red >= 85 && red > green * 1.15 && green > blue * 1.1) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countGoldenPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = argb >> 16 & 255;
                int green = argb >> 8 & 255;
                int blue = argb & 255;
                if ((argb >>> 24) == 255 && red >= 190 && green >= 140 && blue <= 110) {
                    count++;
                }
            }
        }
        return count;
    }
}
