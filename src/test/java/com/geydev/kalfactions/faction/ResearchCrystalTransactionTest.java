package com.geydev.kalfactions.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geydev.kalfactions.claim.ClaimKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ResearchCrystalTransactionTest {
    private static final ResearchNode NODE = ResearchNode.SCI_SMELT;
    private static final int CRYSTAL_COST = 12;

    @Test
    void configuredTierTableUsesSixPlusCap() {
        List<Integer> costs = List.of(12, 24, 36, 48, 64, 96);

        assertEquals(12, ResearchCrystalCosts.forTier(1, costs));
        assertEquals(64, ResearchCrystalCosts.forTier(5, costs));
        assertEquals(96, ResearchCrystalCosts.forTier(6, costs));
        assertEquals(96, ResearchCrystalCosts.forTier(20, costs));
    }

    @Test
    void successfulStartConsumesBothCosts() {
        Fixture fixture = fixture(NODE.cost());
        CrystalWallet wallet = new CrystalWallet().with(InfluenceType.SCIENCE, CRYSTAL_COST);

        FactionManager.StartResearchResult result = start(fixture, wallet);

        assertEquals(FactionManager.StartResearchResult.STARTED, result);
        assertEquals(0L, fixture.faction().influence(InfluenceType.SCIENCE));
        assertEquals(0, wallet.available(InfluenceType.SCIENCE));
        assertEquals(1, wallet.consumeCalls());
        assertTrue(fixture.faction().hasActiveResearch());
    }

    @Test
    void insufficientCrystalsConsumesNothing() {
        Fixture fixture = fixture(NODE.cost());
        CrystalWallet wallet = new CrystalWallet().with(InfluenceType.SCIENCE, CRYSTAL_COST - 1);

        FactionManager.StartResearchResult result = start(fixture, wallet);

        assertEquals(FactionManager.StartResearchResult.INSUFFICIENT_CRYSTALS, result);
        assertEquals(NODE.cost(), fixture.faction().influence(InfluenceType.SCIENCE));
        assertEquals(CRYSTAL_COST - 1, wallet.available(InfluenceType.SCIENCE));
        assertEquals(0, wallet.consumeCalls());
        assertTrue(fixture.faction().activeResearch().isEmpty());
    }

    @Test
    void insufficientInfluenceConsumesNothing() {
        Fixture fixture = fixture(NODE.cost() - 1);
        CrystalWallet wallet = new CrystalWallet().with(InfluenceType.SCIENCE, CRYSTAL_COST);

        FactionManager.StartResearchResult result = start(fixture, wallet);

        assertEquals(FactionManager.StartResearchResult.INSUFFICIENT_INFLUENCE, result);
        assertEquals(NODE.cost() - 1, fixture.faction().influence(InfluenceType.SCIENCE));
        assertEquals(CRYSTAL_COST, wallet.available(InfluenceType.SCIENCE));
        assertEquals(0, wallet.consumeCalls());
    }

    @Test
    void wrongCrystalTypeIsRejected() {
        Fixture fixture = fixture(NODE.cost());
        CrystalWallet wallet = new CrystalWallet().with(InfluenceType.MILITARY, CRYSTAL_COST);

        FactionManager.StartResearchResult result = start(fixture, wallet);

        assertEquals(FactionManager.StartResearchResult.INSUFFICIENT_CRYSTALS, result);
        assertEquals(NODE.cost(), fixture.faction().influence(InfluenceType.SCIENCE));
        assertEquals(CRYSTAL_COST, wallet.available(InfluenceType.MILITARY));
        assertEquals(0, wallet.consumeCalls());
    }

    @Test
    void repeatedRequestCannotChargeTwice() {
        Fixture fixture = fixture(NODE.cost() * 2);
        CrystalWallet wallet = new CrystalWallet().with(InfluenceType.SCIENCE, CRYSTAL_COST * 2);

        assertEquals(FactionManager.StartResearchResult.STARTED, start(fixture, wallet));
        FactionManager.StartResearchResult repeated = start(fixture, wallet);

        assertEquals(FactionManager.StartResearchResult.ALREADY_ACTIVE, repeated);
        assertEquals(NODE.cost(), fixture.faction().influence(InfluenceType.SCIENCE));
        assertEquals(CRYSTAL_COST, wallet.available(InfluenceType.SCIENCE));
        assertEquals(1, wallet.consumeCalls());
    }

    @Test
    void changedPaymentLeavesFactionCostUntouched() {
        Fixture fixture = fixture(NODE.cost());
        ResearchCrystalPayment changed = new ResearchCrystalPayment() {
            @Override
            public int available(InfluenceType type) {
                return CRYSTAL_COST;
            }

            @Override
            public boolean consumeExact(InfluenceType type, int amount) {
                return false;
            }
        };

        FactionManager.StartResearchResult result = start(fixture, changed);

        assertEquals(FactionManager.StartResearchResult.CRYSTAL_PAYMENT_CHANGED, result);
        assertEquals(NODE.cost(), fixture.faction().influence(InfluenceType.SCIENCE));
        assertTrue(fixture.faction().activeResearch().isEmpty());
    }

    private static FactionManager.StartResearchResult start(Fixture fixture, ResearchCrystalPayment payment) {
        return fixture.manager().startResearch(
                fixture.faction().id(),
                NODE,
                1_000L,
                CRYSTAL_COST,
                payment
        );
    }

    private static Fixture fixture(long influence) {
        FactionManager manager = new FactionManager();
        FactionManager.OperationResult created = manager.createFaction(
                UUID.randomUUID(),
                "Research Test",
                new ClaimKey(Level.OVERWORLD, new ChunkPos(0, 0)),
                1
        );
        assertTrue(created.successful());
        Faction faction = manager.getFactionById(created.factionId()).orElseThrow();
        assertTrue(manager.grantInfluence(faction.id(), InfluenceType.SCIENCE, influence).successful());
        return new Fixture(manager, faction);
    }

    private record Fixture(FactionManager manager, Faction faction) {
    }

    private static final class CrystalWallet implements ResearchCrystalPayment {
        private final Map<InfluenceType, Integer> counts = new EnumMap<>(InfluenceType.class);
        private int consumeCalls;

        CrystalWallet with(InfluenceType type, int amount) {
            counts.put(type, amount);
            return this;
        }

        @Override
        public int available(InfluenceType type) {
            return counts.getOrDefault(type, 0);
        }

        @Override
        public boolean consumeExact(InfluenceType type, int amount) {
            consumeCalls++;
            int present = available(type);
            if (present < amount) {
                return false;
            }
            counts.put(type, present - amount);
            return true;
        }

        int consumeCalls() {
            return consumeCalls;
        }
    }
}
