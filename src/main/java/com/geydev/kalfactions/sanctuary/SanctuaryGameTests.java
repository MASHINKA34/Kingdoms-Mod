package com.geydev.kalfactions.sanctuary;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SanctuaryGameTests {
    @GameTest(template = "empty")
    public static void automaticSquareRelocationAndLayerRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos original = level.getSharedSpawnPos();
        BlockPos first = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos second = first.offset(1_024, 0, -1_024);

        manager.relocateAutomaticSpawn(level, first);
        Set<ClaimKey> firstClaims = SanctuaryManager.calculateAutomaticClaims(Level.OVERWORLD, first, 200);
        helper.assertValueEqual(manager.claims().size(), firstClaims.size(), "automatic square size");
        helper.assertTrue(manager.claims().containsAll(firstClaims), "automatic square continuity");

        manager.relocateAutomaticSpawn(level, second);
        Set<ClaimKey> secondClaims = SanctuaryManager.calculateAutomaticClaims(Level.OVERWORLD, second, 200);
        helper.assertTrue(manager.claims().containsAll(secondClaims), "relocated automatic square");
        helper.assertTrue(
                firstClaims.stream()
                        .filter(key -> !secondClaims.contains(key))
                        .noneMatch(manager::isSanctuary),
                "old automatic tail removed"
        );

        ClaimKey automatic = secondClaims.iterator().next();
        ClaimKey manual = new ClaimKey(Level.OVERWORLD, 2_000, 2_000);
        manager.setClaim(automatic, false);
        manager.setClaim(manual, true);
        helper.assertTrue(!manager.isSanctuary(automatic), "automatic chunk explicitly removed");
        helper.assertTrue(manager.isSanctuary(manual), "manual chunk added");
        manager.clearManualClaims(Level.OVERWORLD);
        manager.clearAutomaticSpawn();
        helper.assertTrue(manager.claims().isEmpty(), "both sanctuary layers cleared");

        manager.relocateAutomaticSpawn(level, original);
        helper.succeed();
    }

    private SanctuaryGameTests() {
    }
}
