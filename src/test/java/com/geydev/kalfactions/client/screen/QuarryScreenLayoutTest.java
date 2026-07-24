package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class QuarryScreenLayoutTest {
    @Test
    void layoutUsesContainerOriginAndKeepsActionInsidePanel() {
        QuarryScreen.Layout first = QuarryScreen.layout(306, 146);
        QuarryScreen.Layout second = QuarryScreen.layout(10, 20);

        assertEquals(306, first.panel().x());
        assertEquals(146, first.panel().y());
        assertEquals(390, first.action().x());
        assertEquals(363, first.action().y());
        assertEquals(94, second.action().x());
        assertEquals(237, second.action().y());
        assertTrue(first.action().right() <= first.panel().right());
        assertTrue(first.action().bottom() <= first.panel().bottom());
        assertTrue(first.production().bottom() <= first.action().y());
        assertTrue(first.capture().bottom() <= first.production().y());
    }
}
