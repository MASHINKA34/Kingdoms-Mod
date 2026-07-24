package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DrillScreenLayoutTest {
    @Test
    void workingLayoutUsesContainerOrigin() {
        DrillScreen.Layout layout = new DrillScreen.Layout(37, 19);

        assertEquals(119, layout.targetIconX());
        assertEquals(49, layout.targetIconY());
        assertEquals(295, layout.changeButtonX());
        assertEquals(43, layout.changeButtonY());
    }

    @Test
    void selectorCardsUseSameRectangleForRenderingAndHitTesting() {
        DrillTargetScreen.Layout wide = DrillTargetScreen.Layout.create(960, 540);
        DrillTargetScreen.Rect first = wide.card(0);
        DrillTargetScreen.Rect secondColumn = wide.card(1);

        assertEquals(2, wide.columns());
        assertTrue(first.contains(first.left() + 1, first.top() + 1));
        assertFalse(first.contains(first.right(), first.bottom() - 1));
        assertTrue(secondColumn.left() > first.right());

        DrillTargetScreen.Layout compact = DrillTargetScreen.Layout.create(320, 240);
        assertEquals(1, compact.columns());
        assertEquals(4, compact.perPage());
    }
}
