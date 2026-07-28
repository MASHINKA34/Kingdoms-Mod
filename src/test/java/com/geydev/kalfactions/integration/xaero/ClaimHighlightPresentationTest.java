package com.geydev.kalfactions.integration.xaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.geydev.kalfactions.client.ClientClaimStore;
import com.geydev.kalfactions.quarry.QuarryManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class ClaimHighlightPresentationTest {
    @Test
    void blackZoneUsesDedicatedOpaqueProfile() {
        ClaimHighlightColors.ColorProfile black = ClaimHighlightColors.profile(claim(
                ClientClaimStore.BLACK_ZONE_ID,
                false,
                ""
        ));
        ClaimHighlightColors.ColorProfile faction = ClaimHighlightColors.profile(claim(
                UUID.randomUUID(),
                false,
                "Faction"
        ));

        assertEquals(0x080808, black.color());
        assertEquals(0xB8, black.fillAlpha());
        assertEquals(0xF0, black.borderAlpha());
        assertEquals(0x50, faction.fillAlpha());
    }

    @Test
    void blackZoneMinimapLabelUsesTwoLines() {
        List<Component> lines = new ArrayList<>();

        KingdomsHighlighter.addMinimapLabels(
                claim(ClientClaimStore.BLACK_ZONE_ID, false, ""),
                lines::add
        );

        assertEquals(2, lines.size());
        assertEquals("kingdoms.xaero.black_zone_title", key(lines.get(0)));
        assertEquals("kingdoms.xaero.black_zone_restriction", key(lines.get(1)));
    }

    @Test
    void ownedQuarryMinimapLabelWrapsFactionOntoSecondLine() {
        List<Component> lines = new ArrayList<>();

        KingdomsHighlighter.addMinimapLabels(
                claim(UUID.randomUUID(), true, "asdfasdfasdf"),
                lines::add
        );

        assertEquals(2, lines.size());
        assertEquals("kingdoms.xaero.quarry_title", key(lines.get(0)));
        assertEquals("kingdoms.xaero.faction_line", key(lines.get(1)));
    }

    @Test
    void neutralQuarryHasFallbackLabel() {
        Component label = KingdomsHighlighter.claimLabel(claim(
                QuarryManager.NEUTRAL_MAP_ID,
                true,
                ""
        ));

        assertNotNull(label);
        assertEquals("kingdoms.xaero.quarry_neutral_label", key(label));
    }

    private static ClientClaimStore.ClaimInfo claim(UUID factionId, boolean quarry, String name) {
        return new ClientClaimStore.ClaimInfo(
                0x777777,
                name,
                factionId,
                false,
                false,
                false,
                false,
                quarry
        );
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
