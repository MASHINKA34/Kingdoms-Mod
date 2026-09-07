package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.claim.ClaimKey;
import com.geydev.kalfactions.config.ModConfigSpec;
import com.geydev.kalfactions.faction.FactionManager;
import com.geydev.kalfactions.faction.InfluenceSourceHandler;
import com.geydev.kalfactions.faction.InfluenceType;
import com.geydev.kalfactions.faction.KillRewardLedger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KillRewardGameTests {
    @GameTest(template = "empty", batch = "kill_rewards")
    public static void logoutAndWorldReloadCannotResetPlayerKillRewards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var storage = server.overworld().getDataStorage();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var killer = RegressionPlayers.create(level, pos, 0).player();
        var victim = RegressionPlayers.create(level, pos, 0).player();
        FactionManager original = FactionManager.get(level);
        KillRewardLedger originalLedger = KillRewardLedger.get(server);
        FactionManager manager = new FactionManager();
        helper.assertTrue(manager.createFaction(killer.getUUID(), "Rewards", ClaimKey.of(level, pos), 1)
                .successful(), "the killer has a faction");
        storage.set(FactionManager.DATA_NAME, manager);
        storage.set(KillRewardLedger.DATA_NAME, new KillRewardLedger());
        long originalReward = ModConfigSpec.INFLUENCE_KILL_INFLUENCE.getAsLong();
        int originalCap = ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.getAsInt();
        try {
            ModConfigSpec.INFLUENCE_KILL_INFLUENCE.set(15L);
            ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.set(2);
            var faction = manager.getFactionForMember(killer.getUUID()).orElseThrow();
            long before = faction.influence(InfluenceType.MILITARY);
            for (int count = 0; count < 5; count++) {
                InfluenceSourceHandler.onDeath(new LivingDeathEvent(victim, level.damageSources().playerAttack(killer)));
                InfluenceSourceHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(victim));
                InfluenceSourceHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(killer));
                CompoundTag saved = KillRewardLedger.get(server).save(new CompoundTag(), level.registryAccess());
                storage.set(KillRewardLedger.DATA_NAME,
                        KillRewardLedger.FACTORY.deserializer().apply(saved, level.registryAccess()));
            }
            helper.assertTrue(faction.influence(InfluenceType.MILITARY) - before == 30L,
                    "repeated logout and reload cannot award more than two paid kills");
        } finally {
            ModConfigSpec.INFLUENCE_KILL_INFLUENCE.set(originalReward);
            ModConfigSpec.INFLUENCE_KILL_CAP_PER_VICTIM.set(originalCap);
            storage.set(FactionManager.DATA_NAME, original);
            storage.set(KillRewardLedger.DATA_NAME, originalLedger);
        }
        helper.succeed();
    }

    private KillRewardGameTests() {
    }
}
