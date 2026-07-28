package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QuarryScreenLayoutTest {
    @Test
    void layoutUsesContainerOriginAndKeepsActionInsidePanel() {
        QuarryScreen.Layout first = QuarryScreen.layout(306, 146);
        QuarryScreen.Layout second = QuarryScreen.layout(10, 20);

        assertEquals(390, first.action().x());
        assertEquals(367, first.action().y());
        assertEquals(94, second.action().x());
        assertEquals(241, second.action().y());
        assertEquals(322, first.summary().x());
        assertEquals(316, first.summary().width());
        assertEquals(26, second.details().x());
        assertEquals(316, second.details().width());
        assertTrue(first.action().right() <= 306 + QuarryScreen.PANEL_WIDTH);
        assertTrue(first.action().bottom() <= 146 + QuarryScreen.PANEL_HEIGHT);
        assertTrue(first.production().bottom() <= first.action().y());
        assertTrue(first.capture().bottom() <= first.production().y());
        assertTrue(first.summary().bottom() <= first.details().y());
    }

    @Test
    void detailsBoxFitsTwoLinesOfDisabledReason() {
        QuarryScreen.Layout layout = QuarryScreen.layout(0, 0);
        int lastReasonLineBottom = layout.details().y() + 8 + 36 + 8;

        assertTrue(lastReasonLineBottom <= layout.details().bottom());
    }
}
