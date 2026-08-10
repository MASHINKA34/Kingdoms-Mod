package com.geydev.kalfactions.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class LegacyResearchTest {
    @Test
    void slotsAreOrderedByBonusAndCappedAtTwo() {
        List<FactionBonus> slots = LegacyResearch.slots(Set.of(
                FactionBonus.RESEARCHERS,
                FactionBonus.MINERS,
                FactionBonus.HOOKAH
        ));

        assertEquals(LegacyResearch.MAX_SLOTS, slots.size());
        assertEquals(FactionBonus.MINERS, slots.getFirst());
        assertEquals(FactionBonus.HOOKAH, slots.get(1));
    }

    @Test
    void slotLookupIsStableForEitherIterationOrder() {
        Set<FactionBonus> first = new java.util.LinkedHashSet<>(List.of(FactionBonus.NOMADS, FactionBonus.FARMERS));
        Set<FactionBonus> second = new java.util.LinkedHashSet<>(List.of(FactionBonus.FARMERS, FactionBonus.NOMADS));

        assertEquals(LegacyResearch.slots(first), LegacyResearch.slots(second));
        assertEquals(0, LegacyResearch.slotOf(first, FactionBonus.FARMERS));
        assertEquals(1, LegacyResearch.slotOf(second, FactionBonus.NOMADS));
    }

    @Test
    void everyLegacyNodeChargesAllThreeInfluenceTypes() {
        for (ResearchNode node : ResearchNode.legacyBranch()) {
            assertTrue(node.legacy());
            assertEquals(InfluenceType.VALUES.length, node.costTypes().size());
        }
        assertEquals(List.of(InfluenceType.SCIENCE), ResearchNode.SCI_SMELT.costTypes());
    }

    @Test
    void perTypeShareRoundsUp() {
        assertEquals(67L, LegacyResearch.share(200L, 3));
        assertEquals(40, LegacyResearch.share(120, 3));
        assertEquals(214, LegacyResearch.share(640, 3));
        assertEquals(900L, LegacyResearch.share(900L, 1));
    }

    @Test
    void secondStripStaysLockedForSingleBonusFactions() {
        Faction faction = faction(Set.of(FactionBonus.MINERS));

        assertTrue(faction.legacyBonus(0).isPresent());
        assertTrue(faction.legacyBonus(1).isEmpty());
        assertTrue(faction.isResearchAvailable(ResearchNode.LEG_A_1));
        assertFalse(faction.isResearchAvailable(ResearchNode.LEG_B_1));
    }

    @Test
    void bothStripsOpenForTwoBonusFactions() {
        Faction faction = faction(Set.of(FactionBonus.MINERS, FactionBonus.NOMADS));

        assertEquals(FactionBonus.MINERS, faction.legacyBonus(0).orElseThrow());
        assertEquals(FactionBonus.NOMADS, faction.legacyBonus(1).orElseThrow());
        assertTrue(faction.isResearchAvailable(ResearchNode.LEG_A_1));
        assertTrue(faction.isResearchAvailable(ResearchNode.LEG_B_1));
        assertFalse(faction.isResearchAvailable(ResearchNode.LEG_A_2));
    }

    @Test
    void levelsCountPerStripAndFollowTheBonus() {
        Faction faction = faction(Set.of(FactionBonus.MINERS, FactionBonus.NOMADS));

        assertTrue(faction.grantResearch(ResearchNode.LEG_A_1));
        assertTrue(faction.grantResearch(ResearchNode.LEG_A_2));
        assertTrue(faction.grantResearch(ResearchNode.LEG_B_1));

        assertEquals(2, faction.legacyLevel(0));
        assertEquals(1, faction.legacyLevel(1));
        assertEquals(2, faction.legacyLevel(FactionBonus.MINERS));
        assertEquals(1, faction.legacyLevel(FactionBonus.NOMADS));
        assertEquals(0, faction.legacyLevel(FactionBonus.BUILDERS));
    }

    @Test
    void levelsSurviveSaveAndLoad() {
        Faction faction = faction(Set.of(FactionBonus.MINERS, FactionBonus.NOMADS));
        faction.grantResearch(ResearchNode.LEG_A_1);
        faction.grantResearch(ResearchNode.LEG_A_2);
        faction.grantResearch(ResearchNode.LEG_A_3);
        faction.grantResearch(ResearchNode.LEG_B_1);

        CompoundTag tag = faction.save();
        Faction reloaded = Faction.load(tag).orElseThrow();

        assertEquals(3, reloaded.legacyLevel(0));
        assertEquals(1, reloaded.legacyLevel(1));
        assertEquals(faction.bonuses(), reloaded.bonuses());
    }

    @Test
    void levelsIgnoreMembershipChanges() {
        FactionManager manager = new FactionManager();
        Faction faction = create(manager, Set.of(FactionBonus.MINERS, FactionBonus.NOMADS));
        faction.grantResearch(ResearchNode.LEG_A_1);
        faction.grantResearch(ResearchNode.LEG_A_2);

        UUID joiner = UUID.randomUUID();
        assertTrue(manager.addMember(faction.id(), joiner).successful());
        assertEquals(2, faction.legacyLevel(0));
        assertTrue(manager.removeMember(faction.id(), joiner).successful());

        assertEquals(2, faction.legacyLevel(0));
    }

    private static Faction faction(Set<FactionBonus> bonuses) {
        return create(new FactionManager(), bonuses);
    }

    private static Faction create(FactionManager manager, Set<FactionBonus> bonuses) {
        FactionManager.OperationResult created = manager.createFaction(
                UUID.randomUUID(),
                "Legacy Test",
                0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"),
                bonuses,
                false,
                new ClaimKey(Level.OVERWORLD, new ChunkPos(0, 0)),
                1
        );
        assertTrue(created.successful());
        return manager.getFactionById(created.factionId()).orElseThrow();
    }
}
