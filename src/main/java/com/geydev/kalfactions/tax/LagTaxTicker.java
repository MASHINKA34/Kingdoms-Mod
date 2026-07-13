package com.geydev.kalfactions.tax;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.profiling.ChunkProfiler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class LagTaxTicker {
    private static final int MINUTE_TICKS = 1200;
    private static int ticksUntilMinuteCheck = MINUTE_TICKS;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LagTaxService.initialize(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        ChunkProfiler.onServerTickStart();
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        ChunkProfiler.SampleResult sample = ChunkProfiler.flush(event.getServer());
        if (sample != null) {
            LagTaxService.handleSample(event.getServer(), sample);
        }
        if (--ticksUntilMinuteCheck <= 0) {
            ticksUntilMinuteCheck = MINUTE_TICKS;
            LagTaxService.minuteCheck(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LagTaxService.onLogin(player);
        }
    }

    private LagTaxTicker() {
    }
}
