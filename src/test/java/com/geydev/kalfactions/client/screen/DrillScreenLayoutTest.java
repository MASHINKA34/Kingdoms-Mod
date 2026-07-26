package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
