package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionBonus;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.LegacyResearch;
import com.geydev.kalfactions.faction.ResearchCrystalPayment;
import com.geydev.kalfactions.faction.ResearchNode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyResearchGameTests {
    @GameTest(template = "empty")
    public static void legacyStartChargesEveryInfluenceType(GameTestHelper helper) {
        ResearchNode node = ResearchNode.LEG_A_1;
        long influencePerType = node.influenceCostPerType();
        int crystalsPerType = node.crystalCostPerType(LegacyResearch.crystalCost(node.legacyLevel()));
        Fixture fixture = fixture(Set.of(FactionBonus.MINERS, FactionBonus.NOMADS), influencePerType * 2L);
        Wallet wallet = new Wallet(crystalsPerType * 2);

        FactionManager.StartResearchResult started = fixture.manager().startResearch(
                fixture.faction().id(),
                node,
                1_000L,
                LegacyResearch.crystalCost(node.legacyLevel()),
                wallet
        );

        helper.assertTrue(
                started == FactionManager.StartResearchResult.STARTED,
                "Legacy research did not start: " + started
        );
        for (InfluenceType type : InfluenceType.VALUES) {
            helper.assertTrue(
                    fixture.faction().influence(type) == influencePerType,
                    type + " influence was not charged once: " + fixture.faction().influence(type)
            );
            helper.assertTrue(
                    wallet.available(type) == crystalsPerType,
                    type + " crystals were not charged once: " + wallet.available(type)
            );
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingOneInfluenceTypeBlocksTheStart(GameTestHelper helper) {
        ResearchNode node = ResearchNode.LEG_A_1;
        long influencePerType = node.influenceCostPerType();
        int crystals = LegacyResearch.crystalCost(node.legacyLevel());
        Fixture fixture = fixture(
                Set.of(FactionBonus.MINERS, FactionBonus.NOMADS),
                influencePerType,
                Set.of(InfluenceType.SCIENCE, InfluenceType.ECONOMIC)
        );
        Wallet wallet = new Wallet(crystals);

        FactionManager.StartResearchResult result = fixture.manager().startResearch(
                fixture.faction().id(), node, 1_000L, crystals, wallet
        );

        helper.assertTrue(
                result == FactionManager.StartResearchResult.INSUFFICIENT_INFLUENCE,
                "A missing influence type was accepted: " + result
        );
        helper.assertTrue(
                fixture.faction().influence(InfluenceType.SCIENCE) == influencePerType,
                "Science influence changed on rejection"
        );
        helper.assertTrue(wallet.available(InfluenceType.SCIENCE) == crystals, "Crystals were consumed on rejection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void levelsScaleOnlyTheirOwnBonus(GameTestHelper helper) {
        Fixture fixture = fixture(Set.of(FactionBonus.MINERS, FactionBonus.NOMADS), 0L);
        for (ResearchNode node : ResearchNode.legacySlotNodes(0)) {
            fixture.manager().grantResearch(fixture.faction().id(), node);
        }

        double expected = 1.0D + LegacyResearch.percentPerLevel() * LegacyResearch.MAX_LEVEL;
        double miners = fixture.faction().legacyMultiplier(FactionBonus.MINERS);
        double nomads = fixture.faction().legacyMultiplier(FactionBonus.NOMADS);

        helper.assertTrue(Math.abs(miners - expected) < 1.0E-6D, "Miner legacy multiplier is " + miners);
        helper.assertTrue(Math.abs(nomads - 1.0D) < 1.0E-6D, "Nomad legacy multiplier leaked: " + nomads);
        helper.assertTrue(
                Math.abs(fixture.faction().legacyMultiplier(FactionBonus.BUILDERS) - 1.0D) < 1.0E-6D,
                "An unowned bonus was scaled"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sealedStripCannotBeStarted(GameTestHelper helper) {
        ResearchNode node = ResearchNode.LEG_B_1;
        Fixture fixture = fixture(Set.of(FactionBonus.MINERS), node.influenceCostPerType());
        Wallet wallet = new Wallet(LegacyResearch.crystalCost(node.legacyLevel()));

        FactionManager.StartResearchResult result = fixture.manager().startResearch(
                fixture.faction().id(), node, 1_000L, LegacyResearch.crystalCost(node.legacyLevel()), wallet
        );

        helper.assertTrue(
                result == FactionManager.StartResearchResult.UNAVAILABLE,
                "A sealed legacy strip accepted research: " + result
        );
        helper.assertTrue(fixture.faction().legacyLevel(1) == 0, "A sealed strip reported a level");
        helper.succeed();
    }

    private static Fixture fixture(Set<FactionBonus> bonuses, long influencePerType) {
        return fixture(bonuses, influencePerType, Set.of(InfluenceType.VALUES));
    }

    private static Fixture fixture(Set<FactionBonus> bonuses, long influencePerType, Set<InfluenceType> funded) {
        FactionManager manager = new FactionManager();
        FactionManager.OperationResult created = manager.createFaction(
                UUID.randomUUID(),
                "Legacy Game Test",
                0x4E7A42,
                ResourceLocation.withDefaultNamespace("stone"),
                bonuses,
                false,
                new ClaimKey(Level.OVERWORLD, new ChunkPos(0, 0)),
                1
        );
        Faction faction = manager.getFactionById(created.factionId()).orElseThrow();
        for (InfluenceType type : funded) {
            manager.grantInfluence(faction.id(), type, influencePerType);
        }
        return new Fixture(manager, faction);
    }

    private record Fixture(FactionManager manager, Faction faction) {
    }

    private static final class Wallet implements ResearchCrystalPayment {
        private final Map<InfluenceType, Integer> counts = new EnumMap<>(InfluenceType.class);

        private Wallet(int amountPerType) {
            for (InfluenceType type : InfluenceType.VALUES) {
                counts.put(type, amountPerType);
            }
        }

        @Override
        public int available(InfluenceType type) {
            return counts.getOrDefault(type, 0);
        }

        @Override
        public boolean consumeExact(InfluenceType type, int amount) {
            int available = available(type);
            if (available < amount) {
                return false;
            }
            counts.put(type, available - amount);
            return true;
        }
    }

    private LegacyResearchGameTests() {
    }
}
