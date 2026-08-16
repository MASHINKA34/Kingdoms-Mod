package com.geydev.kalfactions.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.blackzone.BlackZoneData;
import com.geydev.kalfactions.dungeon.ChestTemplateManager;
import com.geydev.kalfactions.dungeon.DungeonManager;
import com.geydev.kalfactions.faith.FaithManager;
import com.geydev.kalfactions.market.MarketPlotManager;
import com.geydev.kalfactions.music.MusicManager;
import com.geydev.kalfactions.news.NewsManager;
import com.geydev.kalfactions.outpost.RogueOutpostManager;
import com.geydev.kalfactions.quarry.QuarryManager;
import com.geydev.kalfactions.sanctuary.SanctuaryExecutionManager;
import com.geydev.kalfactions.sanctuary.SanctuaryManager;
import com.geydev.kalfactions.scout.ScoutManager;
import com.geydev.kalfactions.tax.LagTaxManager;
import com.geydev.kalfactions.tax.OfflineNoticeQueue;
import com.geydev.kalfactions.war.WarHistory;
import com.geydev.kalfactions.war.WarManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class SavedDataVersioningTest {
    private static HolderLookup.Provider registries;

    private static final Map<String, SavedData.Factory<? extends SavedData>> VERSIONED = Map.ofEntries(
            Map.entry("BlackZoneData", BlackZoneData.FACTORY),
            Map.entry("ChestTemplateManager", ChestTemplateManager.FACTORY),
            Map.entry("DungeonManager", DungeonManager.FACTORY),
            Map.entry("FaithManager", FaithManager.FACTORY),
            Map.entry("LagTaxManager", LagTaxManager.FACTORY),
            Map.entry("MarketPlotManager", MarketPlotManager.FACTORY),
            Map.entry("MusicManager", MusicManager.FACTORY),
            Map.entry("NewsManager", NewsManager.FACTORY),
            Map.entry("OfflineNoticeQueue", OfflineNoticeQueue.FACTORY),
            Map.entry("QuarryManager", QuarryManager.FACTORY),
            Map.entry("RogueOutpostManager", RogueOutpostManager.FACTORY),
            Map.entry("SanctuaryExecutionManager", SanctuaryExecutionManager.FACTORY),
            Map.entry("SanctuaryManager", SanctuaryManager.FACTORY),
            Map.entry("ScoutManager", ScoutManager.FACTORY),
            Map.entry("WarHistory", WarHistory.FACTORY),
            Map.entry("WarManager", WarManager.FACTORY)
    );

    @BeforeAll
    static void bootstrap() {
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createLookup();
    }

    @TestFactory
    List<DynamicTest> everyVersionedClassReadsLegacyAndCurrentTagsAlike() {
        return VERSIONED.entrySet().stream()
                .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () -> assertVersioned(entry.getValue())))
                .toList();
    }

    @Test
    void aPopulatedBlackZoneTollSurvivesTheLegacyRead() {
        UUID playerId = UUID.fromString("2b1f5a10-0000-4000-8000-00000000abcd");
        CompoundTag toll = new CompoundTag();
        toll.putUUID("id", playerId);
        toll.putLong("accumulated", 3_600_000L);
        toll.putLong("lastInZone", 1_700_000_000_000L);
        toll.putInt("notifiedStage", 2);
        toll.putLong("lastKolyvan", 1_699_000_000_000L);
        ListTag players = new ListTag();
        players.add(toll);
        CompoundTag legacy = new CompoundTag();
        legacy.put("players", players);

        BlackZoneData loaded = BlackZoneData.FACTORY.deserializer().apply(legacy.copy(), registries);

        assertEquals(3_600_000L, loaded.toll(playerId).accumulatedMillis());
        assertEquals(1_700_000_000_000L, loaded.toll(playerId).lastInZoneAt());
        assertEquals(2, loaded.toll(playerId).notifiedStage());
        assertEquals(1_699_000_000_000L, loaded.toll(playerId).lastKolyvanAt());
        assertReadsAlike(BlackZoneData.FACTORY, legacy);
    }

    @Test
    void aPopulatedSanctuaryExecutionListSurvivesTheLegacyRead() {
        UUID first = UUID.fromString("11111111-2222-4333-8444-555555555555");
        UUID second = UUID.fromString("66666666-7777-4888-8999-aaaaaaaaaaaa");
        ListTag vulnerable = new ListTag();
        vulnerable.add(NbtUtils.createUUID(first));
        vulnerable.add(NbtUtils.createUUID(second));
        CompoundTag legacy = new CompoundTag();
        legacy.put("vulnerable", vulnerable);

        SanctuaryExecutionManager loaded =
                SanctuaryExecutionManager.FACTORY.deserializer().apply(legacy.copy(), registries);

        assertTrue(loaded.isVulnerable(first));
        assertTrue(loaded.isVulnerable(second));
        assertReadsAlike(SanctuaryExecutionManager.FACTORY, legacy);
    }

    @Test
    void aPopulatedOfflineNoticeQueueSurvivesTheLegacyRead() {
        UUID playerId = UUID.fromString("0badf00d-0000-4000-8000-00000000cafe");
        CompoundTag notice = new CompoundTag();
        notice.putString("message", "{\"text\":\"Налог списан\"}");
        notice.putBoolean("successful", false);
        ListTag notices = new ListTag();
        notices.add(notice);
        CompoundTag player = new CompoundTag();
        player.putUUID("id", playerId);
        player.put("notices", notices);
        ListTag players = new ListTag();
        players.add(player);
        CompoundTag legacy = new CompoundTag();
        legacy.put("players", players);

        OfflineNoticeQueue loaded = OfflineNoticeQueue.FACTORY.deserializer().apply(legacy.copy(), registries);

        assertEquals(1, loaded.drain(playerId).size());
        assertReadsAlike(OfflineNoticeQueue.FACTORY, legacy);
    }

    private static <T extends SavedData> void assertVersioned(SavedData.Factory<T> factory) {
        CompoundTag legacy = factory.constructor().get().save(new CompoundTag(), registries);
        assertTrue(
                legacy.contains(SavedDataFormat.TAG_VERSION, Tag.TAG_INT),
                "saving must stamp " + SavedDataFormat.TAG_VERSION
        );
        legacy.remove(SavedDataFormat.TAG_VERSION);

        assertReadsAlike(factory, legacy);
    }

    private static <T extends SavedData> void assertReadsAlike(SavedData.Factory<T> factory, CompoundTag legacy) {
        T fromLegacy = factory.deserializer().apply(legacy.copy(), registries);
        CompoundTag rewritten = fromLegacy.save(new CompoundTag(), registries);

        assertTrue(
                rewritten.contains(SavedDataFormat.TAG_VERSION, Tag.TAG_INT),
                "saving must stamp " + SavedDataFormat.TAG_VERSION
        );
        assertTrue(fromLegacy.isDirty(), "a legacy read must be queued for a rewrite");

        T fromCurrent = factory.deserializer().apply(rewritten.copy(), registries);

        assertEquals(rewritten, fromCurrent.save(new CompoundTag(), registries));
        assertFalse(fromCurrent.isDirty(), "a current read must not be queued for a rewrite");

        CompoundTag stamped = legacy.copy();
        stamped.putInt(SavedDataFormat.TAG_VERSION, SavedDataFormat.LEGACY_VERSION);
        T fromZero = factory.deserializer().apply(stamped, registries);

        assertEquals(rewritten, fromZero.save(new CompoundTag(), registries));
    }
}
