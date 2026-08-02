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
    public static void remoteControlPointDoesNotMoveAutomaticSquare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SanctuaryManager manager = SanctuaryManager.get(level);
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos remoteControl = worldSpawn.offset(1_024, 0, -1_024);
        manager.initializeAutomaticSpawn(level);
        Set<ClaimKey> spawnClaims = SanctuaryManager.calculateAutomaticClaims(
                Level.OVERWORLD,
                worldSpawn,
                200
        );

        manager.initializeAutomaticSpawn(Level.OVERWORLD, remoteControl, 200);
        helper.assertTrue(manager.claims().containsAll(spawnClaims), "spawn square stays in place");

        ClaimKey extension = ClaimKey.of(level, remoteControl);
        manager.setClaim(extension, true);
        helper.assertTrue(manager.isSanctuary(extension), "remote control can extend sanctuary");
        helper.assertTrue(manager.claims().containsAll(spawnClaims), "extension does not move spawn square");
        manager.setClaim(extension, false);
        helper.succeed();
    }

    private SanctuaryGameTests() {
    }
}
