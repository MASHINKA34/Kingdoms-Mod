package com.geydev.kalfactions.gametest;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.net.FactionDirectoryService;
import com.geydev.kalfactions.net.FactionPayloads;
import com.geydev.kalfactions.net.FactionServerHooks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(KalFactions.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionDirectoryGameTests {
    @GameTest(template = "empty", batch = "faction_directory")
    public static void requestSpamAndInvalidInvitesCannotAmplifyDirectoryResponses(GameTestHelper helper) {
        var fixture = RegressionPlayers.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), 0);
        UUID missing = UUID.randomUUID();
        for (int request = 0; request < 1000; request++) {
            FactionDirectoryService.requestFactionList(fixture.player());
            FactionServerHooks.respondInvite(fixture.player(), missing, true);
            FactionServerHooks.respondAlliance(fixture.player(), missing, true);
        }
        helper.assertValueEqual(countLists(fixture), 1L, "packet spam builds and sends one directory response");
        helper.assertValueEqual(fixture.packets().size(), 3, "only directory, badge and one rejection are sent");
        FactionServerHooks.sendFactionList(fixture.player());
        helper.assertValueEqual(countLists(fixture), 2L, "server changes can still force a fresh directory");
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            FactionDirectoryService.requestFactionList(fixture.player());
            helper.assertValueEqual(countLists(fixture), 3L, "request works again after cooldown");
            FactionServerHooks.clearRateLimit(fixture.player().getUUID());
        }).thenSucceed();
    }

    private static long countLists(RegressionPlayers.Fixture fixture) {
        return fixture.packets().stream().filter(packet -> packet instanceof ClientboundCustomPayloadPacket payload
                && payload.payload() instanceof FactionPayloads.S2CFactionList).count();
    }

    private FactionDirectoryGameTests() {
    }
}
