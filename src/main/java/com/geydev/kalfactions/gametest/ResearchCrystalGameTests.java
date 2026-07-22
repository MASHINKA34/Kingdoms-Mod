package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.faction.Faction;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.ResearchCrystalPayment;
import com.geydev.kalfactions.faction.ResearchNode;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchCrystalGameTests {
    @GameTest(template = "empty")
    public static void startAndReplayAreAtomic(GameTestHelper helper) {
        Fixture fixture = fixture(ResearchNode.SCI_SMELT.cost() * 2);
        Wallet wallet = new Wallet(InfluenceType.SCIENCE, 24);

        FactionManager.StartResearchResult started = fixture.manager().startResearch(
                fixture.faction().id(), ResearchNode.SCI_SMELT, 1_000L, 12, wallet
        );
        FactionManager.StartResearchResult replay = fixture.manager().startResearch(
                fixture.faction().id(), ResearchNode.SCI_SMELT, 1_001L, 12, wallet
        );

        helper.assertTrue(started == FactionManager.StartResearchResult.STARTED, "Research did not start");
        helper.assertTrue(replay == FactionManager.StartResearchResult.ALREADY_ACTIVE, "Replay was not rejected");
        helper.assertTrue(wallet.available(InfluenceType.SCIENCE) == 12, "Replay consumed crystals");
        helper.assertTrue(
                fixture.faction().influence(InfluenceType.SCIENCE) == ResearchNode.SCI_SMELT.cost(),
                "Replay consumed influence"
        );
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongCrystalTypeLeavesBothBalances(GameTestHelper helper) {
        Fixture fixture = fixture(ResearchNode.SCI_SMELT.cost());
        Wallet wallet = new Wallet(InfluenceType.MILITARY, 12);

        FactionManager.StartResearchResult result = fixture.manager().startResearch(
                fixture.faction().id(), ResearchNode.SCI_SMELT, 1_000L, 12, wallet
        );

        helper.assertTrue(
                result == FactionManager.StartResearchResult.INSUFFICIENT_CRYSTALS,
                "Wrong crystal type was accepted"
        );
        helper.assertTrue(
                fixture.faction().influence(InfluenceType.SCIENCE) == ResearchNode.SCI_SMELT.cost(),
                "Influence changed on rejection"
        );
        helper.assertTrue(wallet.available(InfluenceType.MILITARY) == 12, "Wrong crystals were consumed");
        helper.succeed();
    }

    private static Fixture fixture(long influence) {
        FactionManager manager = new FactionManager();
        FactionManager.OperationResult created = manager.createFaction(
                UUID.randomUUID(),
                "Game Test",
                new ClaimKey(Level.OVERWORLD, new ChunkPos(0, 0)),
                1
        );
        Faction faction = manager.getFactionById(created.factionId()).orElseThrow();
        manager.grantInfluence(faction.id(), InfluenceType.SCIENCE, influence);
        return new Fixture(manager, faction);
    }

    private record Fixture(FactionManager manager, Faction faction) {
    }

    private static final class Wallet implements ResearchCrystalPayment {
        private final Map<InfluenceType, Integer> counts = new EnumMap<>(InfluenceType.class);

        private Wallet(InfluenceType type, int amount) {
            counts.put(type, amount);
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

    private ResearchCrystalGameTests() {
    }
}
