package com.geydev.kalfactions.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DungeonLootScreenLayoutTest {
    @Test
    void templateRowsFollowTheScrollOffset() {
        int left = 100;
        int top = 60;

        assertEquals(0, DungeonLootScreen.templateRowAt(left, top, 110.0D, 62.0D, 0, 3));
        assertEquals(2, DungeonLootScreen.templateRowAt(left, top, 110.0D, 110.0D, 0, 3));
        assertEquals(-1, DungeonLootScreen.templateRowAt(left, top, 110.0D, 134.0D, 0, 3));
        assertEquals(5, DungeonLootScreen.templateRowAt(left, top, 110.0D, 110.0D, 3, 9));
        assertEquals(-1, DungeonLootScreen.templateRowAt(left, top, 110.0D, 110.0D, 3, 4));
    }

    @Test
    void clicksOutsideTheListNeverHitARow() {
        int left = 100;
        int top = 60;
        int bottom = top + DungeonLootScreen.LIST_ROWS * DungeonLootScreen.LIST_ROW_HEIGHT;

        assertEquals(-1, DungeonLootScreen.templateRowAt(left, top, 99.0D, 62.0D, 0, 8));
        assertEquals(-1, DungeonLootScreen.templateRowAt(left, top, 110.0D, 59.0D, 0, 8));
        assertEquals(-1, DungeonLootScreen.templateRowAt(
                left, top, left + DungeonLootScreen.LIST_WIDTH, 62.0D, 0, 8));
        assertEquals(-1, DungeonLootScreen.templateRowAt(left, top, 110.0D, bottom, 0, 8));
        assertTrue(DungeonLootScreen.insideList(left, top, 110.0D, bottom - 1.0D));
    }

    @Test
    void previewGridMapsEveryPlanSlot() {
        int left = 200;
        int top = 80;

        assertEquals(0, DungeonLootScreen.previewSlotAt(left, top, 200.0D, 80.0D));
        assertEquals(8, DungeonLootScreen.previewSlotAt(left, top, 200.0D + 8 * 18, 80.0D));
        assertEquals(9, DungeonLootScreen.previewSlotAt(left, top, 200.0D, 80.0D + 18));
        assertEquals(26, DungeonLootScreen.previewSlotAt(left, top, 200.0D + 8 * 18 + 17, 80.0D + 53));
        assertEquals(-1, DungeonLootScreen.previewSlotAt(left, top, 199.0D, 80.0D));
        assertEquals(-1, DungeonLootScreen.previewSlotAt(
                left, top, left + DungeonLootScreen.PREVIEW_WIDTH, 80.0D));
        assertEquals(-1, DungeonLootScreen.previewSlotAt(
                left, top, 200.0D, top + DungeonLootScreen.PREVIEW_HEIGHT));
    }

    @Test
    void templateTabStaysInsideThePanel() {
        assertTrue(DungeonLootScreen.PREVIEW_LEFT + DungeonLootScreen.PREVIEW_WIDTH
                <= DungeonLootScreen.TEMPLATE_WIDTH - DungeonLootScreen.LIST_LEFT);
        assertTrue(DungeonLootScreen.LIST_LEFT + DungeonLootScreen.LIST_WIDTH
                < DungeonLootScreen.PREVIEW_LEFT);
        assertTrue(DungeonLootScreen.LAST_ROW_BOTTOM <= DungeonLootScreen.TEMPLATE_HEIGHT);
        assertTrue(DungeonLootScreen.LIST_TOP + DungeonLootScreen.LIST_ROWS * DungeonLootScreen.LIST_ROW_HEIGHT
                < DungeonLootScreen.LAST_ROW_BOTTOM - 40);
        assertTrue(DungeonLootScreen.PREVIEW_TOP + DungeonLootScreen.PREVIEW_HEIGHT
                <= DungeonLootScreen.TEMPLATE_HEIGHT);
        assertTrue(DungeonLootScreen.PLAN_WIDTH < DungeonLootScreen.TEMPLATE_WIDTH);
    }
}
