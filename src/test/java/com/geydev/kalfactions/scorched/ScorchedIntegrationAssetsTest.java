package com.geydev.kalfactions.scorched;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class ScorchedIntegrationAssetsTest {
    @Test
    void dependencyContainsEveryConfiguredItemId() throws IOException {
        try (ZipFile jar = new ZipFile(Path.of("libs", "ScorchedGuns-1.5.jar").toFile())) {
            DisabledRaidFlares.ITEM_IDS.forEach(id -> assertTrue(
                    jar.getEntry("assets/scguns/models/item/" + id.getPath() + ".json") != null,
                    () -> "Missing flare model for " + id
            ));
            GunBenchBlueprintConsumption.BLUEPRINT_IDS.forEach(id -> assertTrue(
                    jar.getEntry("assets/scguns/models/item/" + id.getPath() + ".json") != null,
                    () -> "Missing blueprint model for " + id
            ));
        }
    }

    @Test
    void idsUseTheRegisteredScgunsNamespace() {
        assertTrue(DisabledRaidFlares.ITEM_IDS.stream().allMatch(id -> id.getNamespace().equals("scguns")));
        assertTrue(GunBenchBlueprintConsumption.BLUEPRINT_IDS.stream().allMatch(id -> id.getNamespace().equals("scguns")));
        assertEquals(10, Set.copyOf(DisabledRaidFlares.ITEM_IDS).size());
        assertEquals(10, Set.copyOf(GunBenchBlueprintConsumption.BLUEPRINT_IDS).size());
    }

    @Test
    void noLocalRecipeOverrideStillTurnsSculkTomeIntoBlueprint() throws IOException {
        Path recipes = Path.of("src", "main", "resources", "data", "scguns", "recipe");
        if (!Files.exists(recipes)) {
            return;
        }
        try (var files = Files.walk(recipes)) {
            assertFalse(files.filter(Files::isRegularFile).anyMatch(path -> {
                try {
                    return Files.readString(path).contains("scguns:sculk_tome");
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }));
        }
    }

    @Test
    void lootAndMixinResourcesAreInstalled() throws IOException {
        String globalLoot = Files.readString(Path.of(
                "src", "main", "resources", "data", "neoforge", "loot_modifiers", "global_loot_modifiers.json"
        ));
        String mixins = Files.readString(Path.of("src", "main", "resources", "kingdoms.mixins.json"));
        assertTrue(globalLoot.contains("kingdoms:remove_raid_flares"));
        assertTrue(mixins.contains("DisabledRaidFlareRecipesMixin"));
        assertTrue(mixins.contains("ScorchedGunBenchResultSlotMixin"));
        assertTrue(mixins.contains("ScorchedBlueprintScreenMixin"));
    }
}
