package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DrillScreenLayoutTest {
    @Test
    void workingLayoutUsesContainerOrigin() {
        DrillScreen.Layout layout = new DrillScreen.Layout(37, 19);

        assertEquals(332, layout.targetIconX());
        assertEquals(95, layout.targetIconY());
        assertEquals(303, layout.changeButtonX());
        assertEquals(140, layout.changeButtonY());
    }

    @Test
    void selectorCardsUseSameRectangleForRenderingAndHitTesting() {
        DrillTargetScreen.Layout layout = DrillTargetScreen.Layout.create(960, 540);
        DrillTargetScreen.Rect first = layout.card(0, 0.0D);
        DrillTargetScreen.Rect second = layout.card(1, 0.0D);

        assertTrue(first.contains(first.left() + 1, first.top() + 1));
        assertFalse(first.contains(first.right(), first.bottom() - 1));
        assertTrue(second.top() > first.bottom());
        assertTrue(first.right() <= layout.panelRight());
        assertEquals(layout.listTop(), first.top());

        DrillTargetScreen.Rect scrolled = layout.card(1, 20.0D);
        assertEquals(second.top() - 20, scrolled.top());
    }

    @Test
    void drillAnimationStartsLoopsAndStopsOnStaticFrame() {
        assertFalse(DrillScreen.shouldAnimateDrill(false, 0, 100, false));
        assertTrue(DrillScreen.shouldAnimateDrill(true, 0, 100, false));
        assertTrue(DrillScreen.shouldAnimateDrill(true, 50, 100, false));
        assertFalse(DrillScreen.shouldAnimateDrill(true, 100, 100, false));
        assertFalse(DrillScreen.shouldAnimateDrill(true, 50, 100, true));
        assertTrue(DrillScreen.shouldAnimateDrill(true, 0, 100, false));
        assertEquals(0, DrillScreen.drillAnimationFrame(false, 14));
        assertEquals(1, DrillScreen.drillAnimationFrame(true, 0));
        assertEquals(1, DrillScreen.drillAnimationFrame(true, 1));
        assertEquals(2, DrillScreen.drillAnimationFrame(true, 2));
        assertEquals(7, DrillScreen.drillAnimationFrame(true, 13));
        assertEquals(1, DrillScreen.drillAnimationFrame(true, 14));
        assertEquals(0, DrillScreen.drillAnimationFrame(false, 16));
        assertEquals(1, DrillScreen.drillAnimationFrame(true, -20));
    }

    @Test
    void drillMechanismSheetContainsEightCrispTransparentFrames() throws IOException {
        try (InputStream stream = DrillScreenLayoutTest.class.getResourceAsStream(
                "/assets/kingdoms/textures/gui/drill/drill_mechanism.png"
        )) {
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertEquals(64, image.getWidth());
            assertEquals(58 * 8, image.getHeight());

            int[] hashes = new int[8];
            int transparentPixels = 0;
            for (int frame = 0; frame < 8; frame++) {
                int[] pixels = image.getRGB(0, frame * 58, 64, 58, null, 0, 64);
                hashes[frame] = Arrays.hashCode(pixels);
                for (int pixel : pixels) {
                    int alpha = pixel >>> 24;
                    assertTrue(alpha == 0 || alpha == 255);
                    if (alpha == 0) {
                        transparentPixels++;
                    }
                }
            }
            assertTrue(transparentPixels > 0);
            for (int frame = 0; frame < 8; frame++) {
                assertNotEquals(hashes[frame], hashes[(frame + 1) % 8]);
            }
        }
    }

    @Test
    void drillForegroundKeepsOnlyActiveDrillPixels() throws IOException {
        try (InputStream stream = DrillScreenLayoutTest.class.getResourceAsStream(
                "/assets/kingdoms/textures/gui/drill/drill_foreground.png"
        )) {
            assertNotNull(stream);
            BufferedImage image = ImageIO.read(stream);
            assertEquals(64, image.getWidth());
            assertEquals(58 * 8, image.getHeight());

            int[] hashes = new int[8];
            int[] opaquePixels = new int[8];
            for (int frame = 0; frame < 8; frame++) {
                int[] pixels = image.getRGB(0, frame * 58, 64, 58, null, 0, 64);
                hashes[frame] = Arrays.hashCode(pixels);
                for (int pixel : pixels) {
                    int alpha = pixel >>> 24;
                    assertTrue(alpha == 0 || alpha == 255);
                    if (alpha == 255) {
                        opaquePixels[frame]++;
                    }
                }
            }
            assertEquals(0, opaquePixels[0]);
            for (int frame = 1; frame < 8; frame++) {
                assertTrue(opaquePixels[frame] > 0);
                assertNotEquals(hashes[frame], hashes[frame == 7 ? 1 : frame + 1]);
            }
        }
    }
}
