package com.geydev.kalfactions.outpost.trader;

import com.geydev.kalfactions.KalFactions;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TradeReplayGameTests {
    @GameTest(template = "empty")
    public static void replayedTransactionHasNoSecondEffect(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID playerId = UUID.randomUUID();
        UUID traderId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        UUID sessionId = TradeSessionManager.open(server, playerId, traderId, now);
        int effects = 0;
        if (TradeSessionManager.validate(server, playerId, traderId, sessionId, 1L, now)
                == TradeSessionManager.Validation.ACCEPTED) {
            effects++;
        }
        if (TradeSessionManager.validate(server, playerId, traderId, sessionId, 1L, now)
                == TradeSessionManager.Validation.ACCEPTED) {
            effects++;
        }

        helper.assertTrue(effects == 1, "A replayed transaction produced a second effect");
        TradeSessionManager.clear(server);
        helper.succeed();
    }

    private TradeReplayGameTests() {
    }
}
