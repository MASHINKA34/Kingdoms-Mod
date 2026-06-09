package com.geydev.kalfactions.war;

import com.geydev.kalfactions.KalFactions;
import com.geydev.kalfactions.config.ModConfigSpec;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives war rollback once per server tick. {@link WarManager#tick} is a cheap no-op while no war is
 * ending, so polling every tick is fine and keeps the throttled rollback (N chunks per tick) moving.
 */
@EventBusSubscriber(modid = KalFactions.MOD_ID)
public final class WarTicker {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        WarManager.get(server).tick(
            server,
            ModConfigSpec.WAR_ROLLBACK_CHUNKS_PER_TICK.getAsInt(),
            ModConfigSpec.WAR_AUTO_END_TICKS.getAsLong()
        );
    }

    private WarTicker() {
    }
}
