package com.geydev.kalfactions.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DungeonNameLimitsTest {
    private static final String ASTRAL = "👑";

    @BeforeAll
    static void bootstrap() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dungeonNamesNeverExceedTheWireLimit() {
        assertTrue(DungeonManager.normalizeName("x".repeat(400)).length() <= DungeonManager.MAX_NAME_CHARS);
        assertTrue(
                DungeonManager.normalizeName(ASTRAL.repeat(400)).length() <= DungeonManager.MAX_NAME_CHARS
        );
    }

    @Test
    void chestTemplateNamesAndAuthorsNeverExceedTheWireLimit() {
        assertTrue(ChestTemplate.normalizeName("x".repeat(400)).length() <= ChestTemplate.MAX_NAME_CHARS);
        assertTrue(
                ChestTemplate.normalizeName(ASTRAL.repeat(400)).length() <= ChestTemplate.MAX_NAME_CHARS
        );

        ChestTemplate template = new ChestTemplate(null, ASTRAL.repeat(400), ASTRAL.repeat(400), 1L, -1, List.of());

        assertTrue(template.name().length() <= ChestTemplate.MAX_NAME_CHARS);
        assertTrue(template.author().length() <= ChestTemplate.MAX_AUTHOR_CHARS);
    }

    @Test
    void theCodePointLimitIsWhyTheWireLimitIsWiderThanTheNameLimit() {
        assertEquals(
                DungeonManager.MAX_NAME_CHARS,
                DungeonManager.normalizeName(ASTRAL.repeat(400)).length()
        );
        assertEquals(
                ChestTemplate.MAX_NAME_CHARS,
                ChestTemplate.normalizeName(ASTRAL.repeat(400)).length()
        );
    }
}
