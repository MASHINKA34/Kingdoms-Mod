package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.outpost.cluster.distribution.FiniteResourceLedger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResourceDepositGameTests {
    @GameTest(template = "empty")
    public static void finiteExtraction(GameTestHelper helper) {
        FiniteResourceLedger.Extraction first = FiniteResourceLedger.extract(40, 1);
        FiniteResourceLedger.Extraction drill = FiniteResourceLedger.extract(first.remaining(), 32);
        FiniteResourceLedger.Extraction finalBatch = FiniteResourceLedger.extract(drill.remaining(), 32);
        helper.assertValueEqual(first.extracted() + drill.extracted() + finalBatch.extracted(), 40, "total extraction");
        helper.assertValueEqual(finalBatch.remaining(), 0, "remaining reserve");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fortuneAndSilkChargeOneBlock(GameTestHelper helper) {
        FiniteResourceLedger.Extraction fortune = FiniteResourceLedger.extract(10, 1);
        FiniteResourceLedger.Extraction silk = FiniteResourceLedger.extract(fortune.remaining(), 1);
        helper.assertValueEqual(silk.remaining(), 8, "reserve after two block removals");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explosionAndMechanicalRemovalShareLedger(GameTestHelper helper) {
        FiniteResourceLedger.Extraction explosion = FiniteResourceLedger.extract(3, 1);
        FiniteResourceLedger.Extraction mechanical = FiniteResourceLedger.extract(explosion.remaining(), 1);
        FiniteResourceLedger.Extraction replay = FiniteResourceLedger.extract(mechanical.remaining(), 0);
        helper.assertValueEqual(replay.remaining(), 1, "idempotent reserve");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exhaustedDrillProducesNothing(GameTestHelper helper) {
        FiniteResourceLedger.Extraction exhausted = FiniteResourceLedger.extract(0, 32);
        helper.assertValueEqual(exhausted.extracted(), 0, "exhausted output");
        helper.assertValueEqual(exhausted.remaining(), 0, "exhausted reserve");
        helper.succeed();
    }

    private ResourceDepositGameTests() {
    }
}
