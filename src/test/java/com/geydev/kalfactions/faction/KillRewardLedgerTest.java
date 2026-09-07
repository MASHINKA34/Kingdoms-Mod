package com.geydev.kalfactions.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class KillRewardLedgerTest {
    private static final UUID KILLER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID VICTIM = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final long NOW = 1_800_000_000_000L;
    private static final long DAY = KillRewardLedger.DAY_MILLIS;

    @Test
    void playerLimitSurvivesReloadAndExpiresAtTheWindowBoundary() {
        KillRewardLedger ledger = new KillRewardLedger();
        for (int count = 0; count < 5; count++) {
            assertEquals(15L, ledger.awardPlayerKill(KILLER, VICTIM, NOW, DAY, 5, () -> 15L));
        }
        KillRewardLedger reloaded = reload(ledger);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(0L, reloaded.awardPlayerKill(KILLER, VICTIM, NOW + DAY - 1, DAY, 5, () -> {
            calls.incrementAndGet();
            return 15L;
        }));
        assertEquals(0, calls.get());
        assertEquals(15L, reloaded.awardPlayerKill(KILLER, VICTIM, NOW + DAY, DAY, 5, () -> 15L));
    }

    @Test
    void playerPairsAreIndependentAndFailedGrantsDoNotConsumeTheLimit() {
        KillRewardLedger ledger = new KillRewardLedger();
        assertEquals(0L, ledger.awardPlayerKill(KILLER, VICTIM, NOW, DAY, 1, () -> 0L));
        assertEquals(15L, ledger.awardPlayerKill(KILLER, VICTIM, NOW, DAY, 1, () -> 15L));
        assertEquals(15L, ledger.awardPlayerKill(VICTIM, KILLER, NOW, DAY, 1, () -> 15L));
    }

    @Test
    void mobProgressAndActualAwardAmountsSurviveReload() {
        KillRewardLedger ledger = new KillRewardLedger();
        for (int count = 0; count < 4; count++) {
            assertEquals(0L, ledger.awardMobKill(KILLER, NOW, 5, 7L, 10L, amount -> amount));
        }
        KillRewardLedger reloaded = reload(ledger);
        assertEquals(7L, reloaded.awardMobKill(KILLER, NOW, 5, 7L, 10L, amount -> amount));
        reloaded = reload(reloaded);
        assertEquals(3L, reloaded.awardMobKill(KILLER, NOW, 1, 9L, 10L, amount -> amount));
        assertEquals(0L, reloaded.awardMobKill(KILLER, NOW, 1, 9L, 10L, amount -> amount));
        assertEquals(9L, reloaded.awardMobKill(KILLER, NOW + DAY, 1, 9L, 10L, amount -> amount));
    }

    @Test
    void changingTheRewardDoesNotRevaluePreviousAwards() {
        KillRewardLedger ledger = new KillRewardLedger();
        assertEquals(7L, ledger.awardMobKill(KILLER, NOW, 1, 7L, 10L, amount -> amount));
        assertEquals(2L, ledger.awardMobKill(KILLER, NOW, 1, 2L, 10L, amount -> amount));
        assertEquals(1L, ledger.awardMobKill(KILLER, NOW, 1, 7L, 10L, amount -> amount));
    }

    @Test
    void clockMovingBackwardsDoesNotResetPlayerLimit() {
        KillRewardLedger ledger = new KillRewardLedger();
        ledger.awardPlayerKill(KILLER, VICTIM, NOW, DAY, 1, () -> 15L);
        assertEquals(0L, reload(ledger).awardPlayerKill(KILLER, VICTIM, NOW - DAY, DAY, 1, () -> 15L));
    }

    @Test
    void cleanupRemovesExpiredOfflinePlayers() {
        KillRewardLedger ledger = new KillRewardLedger();
        ledger.awardPlayerKill(KILLER, VICTIM, NOW, DAY, 1, () -> 15L);
        ledger.awardMobKill(KILLER, NOW, 1, 7L, 10L, amount -> amount);
        ledger.cleanup(NOW + DAY, DAY);
        CompoundTag saved = ledger.save(new CompoundTag(), null);
        assertTrue(saved.getList("playerAwards", 10).isEmpty());
        assertTrue(saved.getList("mobRewards", 10).isEmpty());
        assertFalse(reload(ledger).isDirty());
    }

    @Test
    void separateWorldsDoNotShareLimits() {
        KillRewardLedger first = new KillRewardLedger();
        first.awardPlayerKill(KILLER, VICTIM, NOW, DAY, 1, () -> 15L);
        assertEquals(15L, new KillRewardLedger().awardPlayerKill(KILLER, VICTIM, NOW, DAY, 1, () -> 15L));
    }

    private static KillRewardLedger reload(KillRewardLedger ledger) {
        return KillRewardLedger.FACTORY.deserializer().apply(ledger.save(new CompoundTag(), null), null);
    }
}
