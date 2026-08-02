package com.geydev.kalfactions.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FactionListOpenerTest {
    @Test
    void hidesAllCustomNpcInventoryTabsOnly() {
        assertTrue(FactionListOpener.isCustomNpcInventoryTab(
                "noppes.npcs.client.gui.player.tabs.InventoryTabVanilla"
        ));
        assertTrue(FactionListOpener.isCustomNpcInventoryTab(
                "noppes.npcs.client.gui.player.tabs.InventoryTabFactions"
        ));
        assertTrue(FactionListOpener.isCustomNpcInventoryTab(
                "noppes.npcs.client.gui.player.tabs.InventoryTabQuests"
        ));
        assertFalse(FactionListOpener.isCustomNpcInventoryTab(
                "com.geydev.kalfactions.client.screen.InviteBadgeButton"
        ));
    }
}
